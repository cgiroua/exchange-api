/** Services routes for all of the /orgs/{orgid}/nodes api methods. */
package com.horizon.exchangeapi

import com.horizon.exchangeapi.tables._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.{write, read}
import org.scalatra._
import org.scalatra.swagger._
import org.slf4j._
import slick.jdbc.PostgresProfile.api._

import scala.collection.immutable._
import scala.collection.mutable.{ListBuffer, HashMap => MutableHashMap}
import scala.util._
import scala.util.control.Breaks._

//====== These are the input and output structures for /orgs/{orgid}/nodes routes. Swagger and/or json seem to require they be outside the trait.

/** Output format for GET /orgs/{orgid}/nodes */
case class GetNodesResponse(nodes: Map[String,Node], lastIndex: Int)
case class GetNodeAttributeResponse(attribute: String, value: String)

/** Input for pattern-based search for nodes to make agreements with. */
case class PostPatternSearchRequest(serviceUrl: String, nodeOrgids: Option[List[String]], secondsStale: Int, startIndex: Int, numEntries: Int) {
  def validate() = { }
}

// Tried this to have names on the tuple returned from the db, but didn't work...
case class PatternSearchHashElement(msgEndPoint: String, publicKey: String, noAgreementYet: Boolean)

case class PatternNodeResponse(id: String, msgEndPoint: String, publicKey: String)
case class PostPatternSearchResponse(nodes: List[PatternNodeResponse], lastIndex: Int)


case class PostNodeHealthRequest(lastTime: String, nodeOrgids: Option[List[String]]) {
  def validate() = {}
}

case class NodeHealthAgreementElement(lastUpdated: String)
class NodeHealthHashElement(var lastHeartbeat: String, var agreements: Map[String,NodeHealthAgreementElement])
case class PostNodeHealthResponse(nodes: Map[String,NodeHealthHashElement])


/** Input for service-based (citizen scientist) search, POST /orgs/"+orgid+"/search/nodes */
case class PostSearchNodesRequest(desiredServices: List[RegServiceSearch], secondsStale: Int, propertiesToReturn: Option[List[String]], startIndex: Int, numEntries: Int) {
  /** Halts the request with an error msg if the user input is invalid. */
  def validate() = {
    for (svc <- desiredServices) {
      // now we support more than 1 agreement for a service
      svc.validate match {
        case Some(s) => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, s))
        case None => ;
      }
    }
  }

  /** Returns the services that match all of the search criteria */
  def matches(nodes: Map[String,Node], agHash: AgreementsHash)(implicit logger: Logger): PostSearchNodesResponse = {
    // logger.trace(agHash.agHash.toString)

    // Loop thru the existing nodes and services in the DB. (Should probably make this more FP style)
    var nodesResp: List[NodeResponse] = List()
    for ((id,d) <- nodes) {       // the db query now filters out stale nodes
      // Get all services for this node that are not max'd out on agreements
      var availableServices: List[RegService] = List()
      for (m <- d.registeredServices) {
        breakable {
          // do not even bother checking this against the search criteria if this service is already at its agreement limit
          val agNode = agHash.agHash.get(id)
          agNode match {
            case Some(agNode2) => val agNum = agNode2.get(m.url)  // m.url is the composite org/svcurl
              agNum match {
                case Some(agNum2) => if (agNum2 >= m.numAgreements) break // this is really a continue
                case None => ; // no agreements for this service, nothing to do
              }
            case None => ; // no agreements for this node, nothing to do
          }
          availableServices = availableServices :+ m
        }
      }

      // We now have several services for 1 node from the db (that are not max'd out on agreements). See if all of the desired services are satisfied.
      var servicesResp: List[RegService] = List()
      breakable {
        for (desiredService <- desiredServices) {
          var found: Boolean = false
          breakable {
            for (availableService <- availableServices) {
              if (desiredService.matches(availableService)) {
                servicesResp = servicesResp :+ availableService
                found = true
                break
              }
            }
          }
          if (!found) break // we did not find one of the required services, so end early
        }
      }

      if (servicesResp.length == desiredServices.length) {
        // all required services were available in this node, so add this node to the response list
        nodesResp = nodesResp :+ NodeResponse(id, d.name, servicesResp, d.msgEndPoint, d.publicKey)
      }
    }
    // return the search result to the rest client
    PostSearchNodesResponse(nodesResp, 0)
  }
}

case class NodeResponse(id: String, name: String, services: List[RegService], msgEndPoint: String, publicKey: String)
case class PostSearchNodesResponse(nodes: List[NodeResponse], lastIndex: Int)

/** Input format for PUT /orgs/{orgid}/nodes/<node-id> */
case class PutNodesRequest(token: String, name: String, pattern: String, registeredServices: Option[List[RegService]], msgEndPoint: Option[String], softwareVersions: Option[Map[String,String]], publicKey: String) {
  protected implicit val jsonFormats: Formats = DefaultFormats

  /** Halts the request with an error msg if the user input is invalid. */
  def validate() = {
    // if (publicKey == "") halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "publicKey must be specified."))  <-- skipping this check because POST /agbots/{id}/msgs checks for the publicKey
    if (token == "") halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "the token specified must not be blank"))
    if (pattern != "" && """.*/.*""".r.findFirstIn(pattern).isEmpty) halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "the 'pattern' attribute must have the orgid prepended, with a slash separating"))
    for (m <- registeredServices.getOrElse(List())) {
      // now we support more than 1 agreement for a MS
      // if (m.numAgreements != 1) halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "invalid value "+m.numAgreements+" for numAgreements in "+m.url+". Currently it must always be 1."))
      m.validate match {
        case Some(s) => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, s))
        case None => ;
      }
    }
  }

  /** Get the db actions to insert or update all parts of the node */
  def getDbUpsert(id: String, orgid: String, owner: String): DBIO[_] = {
    // default new field configState in registeredServices
    val rsvc2 = registeredServices.getOrElse(List()).map(rs => RegService(rs.url,rs.numAgreements, rs.configState.orElse(Some("active")), rs.policy, rs.properties))
    NodeRow(id, orgid, token, name, owner, pattern, write(rsvc2), msgEndPoint.getOrElse(""), write(softwareVersions), ApiTime.nowUTC, publicKey).upsert
  }

  /** Get the db actions to update all parts of the node. This is run, instead of getDbUpsert(), when it is a node doing it,
   * because we can't let a node create new nodes. */
  def getDbUpdate(id: String, orgid: String, owner: String): DBIO[_] = {
    // default new field configState in registeredServices
    val rsvc2 = registeredServices.getOrElse(List()).map(rs => RegService(rs.url,rs.numAgreements, rs.configState.orElse(Some("active")), rs.policy, rs.properties))
    NodeRow(id, orgid, token, name, owner, pattern, write(rsvc2), msgEndPoint.getOrElse(""), write(softwareVersions), ApiTime.nowUTC, publicKey).update
  }

  /** Not used any more, kept for reference of how to access object store - Returns the microservice templates for the registeredMicroservices in this object
  def getMicroTemplates: Map[String,String] = {
    if (ExchConfig.getBoolean("api.microservices.disable")) return Map[String,String]()
    val resp = new MutableHashMap[String, String]()
    for (m <- registeredMicroservices) {
      // parse the microservice name out of the specRef url
      val R = ExchConfig.getString("api.specRef.prefix")+"(.*)"+ExchConfig.getString("api.specRef.suffix")+"/?"
      val R2 = R.r
      val microName = m.url match {
        case R2(mNname) => mNname
        case _ => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Incorrect format for microservice url '"+m.url+"'"))
      }

      // find arch and version properties
      val arch = m.properties.find(p => p.name=="arch").map[String](p => p.value).orNull
      if (arch == null) halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Arch property is not specified for microservice '"+m.url+"'"))
      val version = m.properties.find(p => p.name=="version").map[String](p => p.value).orNull
      if (version == null) halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Version property is not specified for microservice '"+m.url+"'"))
      val versObj = Version(version)

      // Get the microservice template from softlayer object store
      val microTmplName = microName+"-"+arch+"-"+versObj
      val objStoreUrl = ExchConfig.getString("api.objStoreTmpls.prefix")+"/"+ExchConfig.getString("api.objStoreTmpls.microDir")+"/"+microTmplName+ExchConfig.getString("api.objStoreTmpls.suffix")
      var response: HttpResponse[String] = null
      try {     // the http request can throw java.net.SocketTimeoutException: connect timed out
        response = scalaj.http.Http(objStoreUrl).headers(("Accept","application/json")).asString
      } catch { case e: Exception => halt(HttpCode.INTERNAL_ERROR, ApiResponse(ApiResponseType.INTERNAL_ERROR, "Exception thrown while trying to get '"+objStoreUrl+"' from Softlayer object storage: "+e)) }
      if (response.code != HttpCode.OK) halt(response.code, ApiResponse(ApiResponseType.BAD_INPUT, "Microservice template for '"+microTmplName+"' not found"))
      resp.put(m.url, response.body)
    }
    resp.toMap
  }
  */
}

case class PatchNodesRequest(token: Option[String], name: Option[String], pattern: Option[String], registeredServices: Option[List[RegService]], msgEndPoint: Option[String], softwareVersions: Option[Map[String,String]], publicKey: Option[String]) {
  protected implicit val jsonFormats: Formats = DefaultFormats

  /** Returns a tuple of the db action to update parts of the node, and the attribute name being updated. */
  def getDbUpdate(id: String): (DBIO[_],String) = {
    val lastHeartbeat = ApiTime.nowUTC
    //todo: support updating more than 1 attribute, but i think slick does not support dynamic db field names
    // find the 1st non-blank attribute and create a db action to update it for this node
    token match {
      case Some(token2) => if (token2 == "") halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "the token can not be set to the empty string"))
        val tok = if (Password.isHashed(token2)) token2 else Password.hash(token2)
        return ((for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.token,d.lastHeartbeat)).update((id, tok, lastHeartbeat)), "token")
      case _ => ;
    }
    softwareVersions match {
      case Some(swv) => val swVersions = if (swv.nonEmpty) write(softwareVersions) else ""
        return ((for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.softwareVersions,d.lastHeartbeat)).update((id, swVersions, lastHeartbeat)), "softwareVersions")
      case _ => ;
    }
    registeredServices match {
      case Some(rsvc) => val regSvc = if (rsvc.nonEmpty) write(registeredServices) else ""
        return ((for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.regServices,d.lastHeartbeat)).update((id, regSvc, lastHeartbeat)), "registeredServices")
      case _ => ;
    }
    name match { case Some(name2) => return ((for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.name,d.lastHeartbeat)).update((id, name2, lastHeartbeat)), "name"); case _ => ; }
    pattern match { case Some(pattern2) => return ((for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.pattern,d.lastHeartbeat)).update((id, pattern2, lastHeartbeat)), "pattern"); case _ => ; }
    msgEndPoint match { case Some(msgEndPoint2) => return ((for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.msgEndPoint,d.lastHeartbeat)).update((id, msgEndPoint2, lastHeartbeat)), "msgEndPoint"); case _ => ; }
    publicKey match { case Some(publicKey2) => return ((for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.publicKey,d.lastHeartbeat)).update((id, publicKey2, lastHeartbeat)), "publicKey"); case _ => ; }
    return (null, null)
  }
}

case class PostNodeConfigStateRequest(org: String, url: String, configState: String) {
  protected implicit val jsonFormats: Formats = DefaultFormats
  //def logger: Logger    // get access to the logger object in ExchangeApiApp

  def validate() = {
    if (configState != "suspended" && configState != "active") halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "the configState value must be either 'suspended' or 'active'."))
  }

  // Match registered service urls (which are org/url) to the input org and url
  def isMatch(compositeUrl: String): Boolean = {
    val reg = """^(\S+?)/(\S+)$""".r
    val (comporg, compurl) = compositeUrl match {
      case reg(o,u) => (o, u)
      case _ => halt(HttpCode.INTERNAL_ERROR, ApiResponse(ApiResponseType.INTERNAL_ERROR, "node registeredService url '"+compositeUrl+"' is not in valid form 'org/url'."))
    }
    (org, url) match {
      case ("","") => return true
      case ("",u) => return compurl == u
      case (o,"") => return comporg == o
      case (o,u) => return comporg == o && compurl == u
    }
  }

  // Given the existing list of registered svcs in the db for this node, determine the db update necessary to apply the new configState
  def getDbUpdate(regServices: String, id: String): DBIO[_] = {
    if (regServices == "") halt(HttpCode.NOT_FOUND, ApiResponse(ApiResponseType.NOT_FOUND, "node has no registeredServices to change the configState of."))
    val regSvcs = read[List[RegService]](regServices)
    if (regSvcs.isEmpty) halt(HttpCode.NOT_FOUND, ApiResponse(ApiResponseType.NOT_FOUND, "node has no registeredServices to change the configState of."))

    // Copy the list of required svcs, changing configState wherever it applies
    var matchingSvcFound = false
    val newRegSvcs = regSvcs.map({ rs =>
      if (isMatch(rs.url)) {
        matchingSvcFound = true   // warning: intentional side effect (didnt know how else to do it)
        if (configState != rs.configState.getOrElse("")) RegService(rs.url,rs.numAgreements, Some(configState), rs.policy, rs.properties)
        else rs
      }
      else rs
    })
    // this check is not ok, because we should not return NOT_FOUND if we find matching svc but their configState is already set the requested value
    //if (newRegSvcs.sameElements(regSvcs)) halt(HttpCode.NOT_FOUND, ApiResponse(ApiResponseType.NOT_FOUND, "did not find any registeredServices that matched the given org and url criteria."))
    if (!matchingSvcFound) halt(HttpCode.NOT_FOUND, ApiResponse(ApiResponseType.NOT_FOUND, "did not find any registeredServices that matched the given org and url criteria."))
    if (newRegSvcs == regSvcs) {
      println("No db update necessary, all relevant config states already correct")
      //logger.debug("No db update necessary, all relevant config states already correct")
      return DBIO.successful(1)    // all the configStates were already set correctly, so nothing to do
    }

    // Convert from struct back to string and return db action to update that
    val newRegSvcsString = write(newRegSvcs)
    return (for { d <- NodesTQ.rows if d.id === id } yield (d.id,d.regServices,d.lastHeartbeat)).update((id, newRegSvcsString, ApiTime.nowUTC))
  }
}

case class PutNodeStatusRequest(connectivity: Map[String,Boolean], services: List[OneService]) {
  protected implicit val jsonFormats: Formats = DefaultFormats
  def validate() = { }

  def toNodeStatusRow(nodeId: String) = NodeStatusRow(nodeId, write(connectivity), write(services), ApiTime.nowUTC)
}


/** Output format for GET /orgs/{orgid}/nodes/{id}/agreements */
case class GetNodeAgreementsResponse(agreements: Map[String,NodeAgreement], lastIndex: Int)

/** Input format for PUT /orgs/{orgid}/nodes/{id}/agreements/<agreement-id> */
case class PutNodeAgreementRequest(services: Option[List[NAService]], agreementService: Option[NAgrService], state: String) {
  protected implicit val jsonFormats: Formats = DefaultFormats
  def validate() = {
    if (services.isEmpty && agreementService.isEmpty) {
      halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "you must specify at least 1 of 'services' or 'agreementService'."))
    }
  }

  def toNodeAgreementRow(nodeId: String, agId: String) = {
    if (agreementService.isDefined) NodeAgreementRow(agId, nodeId, write(services), agreementService.get.orgid, agreementService.get.pattern, agreementService.get.url, state, ApiTime.nowUTC)
    else NodeAgreementRow(agId, nodeId, write(services), "", "", "", state, ApiTime.nowUTC)
  }
}


/** Input body for POST /orgs/{orgid}/nodes/{id}/msgs */
case class PostNodesMsgsRequest(message: String, ttl: Int)

/** Response for GET /orgs/{orgid}/nodes/{id}/msgs */
case class GetNodeMsgsResponse(messages: List[NodeMsg], lastIndex: Int)


/** Implementation for all of the /orgs/{orgid}/nodes routes */
trait NodesRoutes extends ScalatraBase with FutureSupport with SwaggerSupport with AuthenticationSupport {
  def db: Database      // get access to the db object in ExchangeApiApp
  implicit def logger: Logger    // get access to the logger object in ExchangeApiApp
  protected implicit def jsonFormats: Formats
  // implicit def formats: org.json4s.Formats{val dateFormat: org.json4s.DateFormat; val typeHints: org.json4s.TypeHints}

  /* ====== GET /orgs/{orgid}/nodes ================================
    This is of type org.scalatra.swagger.SwaggerOperation
    apiOperation() is a method of org.scalatra.swagger.SwaggerSupport. It returns org.scalatra.swagger.SwaggerSupportSyntax$$OperationBuilder
    and then all of the other methods below that (summary, notes, etc.) are all part of OperationBuilder and return OperationBuilder.
    So instead of the infix invocation in the code below, we could alternatively code it like this:
    val getNodes = apiOperation[GetNodesResponse]("getNodes").summary("Returns matching nodes").description("Based on the input selection criteria, returns the matching nodes (RPis) in the exchange DB.")
  */
  val getNodes =
    (apiOperation[GetNodesResponse]("getNodes")
      summary("Returns all nodes")
      description("""Returns all nodes (RPis) in the exchange DB. Can be run by a user or agbot (but not a node).""")
      // authorizations("basicAuth")
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("Username of exchange user, or ID of an agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("token", DataType.String, Option[String]("Password of exchange user or token of the agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("idfilter", DataType.String, Option[String]("Filter results to only include nodes with this id (can include % for wildcard - the URL encoding for % is %25)"), paramType=ParamType.Query, required=false),
        Parameter("name", DataType.String, Option[String]("Filter results to only include nodes with this name (can include % for wildcard - the URL encoding for % is %25)"), paramType=ParamType.Query, required=false),
        Parameter("owner", DataType.String, Option[String]("Filter results to only include nodes with this owner (can include % for wildcard - the URL encoding for % is %25)"), paramType=ParamType.Query, required=false)
        )
      // this does not work, because scalatra will not give me the request.body on a GET
      // parameters(Parameter("body", DataType[GetNodeRequest], Option[String]("Node search criteria"), paramType = ParamType.Body))
      responseMessages(ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  /** operation() is a method of org.scalatra.swagger.SwaggerSupport that takes SwaggerOperation and returns RouteTransformer */
  get("/orgs/:orgid/nodes", operation(getNodes)) ({
    // try {    // this try/catch does not get us much more than what scalatra does by default
    // I think the request member is of type org.eclipse.jetty.server.Request, which implements interfaces javax.servlet.http.HttpServletRequest and javax.servlet.ServletRequest
    val orgid = params("orgid")
    val ident = authenticate().authorizeTo(TNode(OrgAndId(orgid,"*").toString),Access.READ)
    val superUser = ident.isSuperUser
    val resp = response
    // throw new IllegalArgumentException("arg 1 was wrong...")
    // The nodes, microservices, and properties tables all combine to form the Node object, so we do joins to get them all.
    // Note: joinLeft is necessary here so that if no micros exist for a node, we still get the node (and likewise for the micro if no props exist).
    //    This means m and p below are wrapped in Option because they may not always be there
    //var q = for {
    //  ((d, m), p) <- NodesTQ.getAllNodes(orgid) joinLeft RegMicroservicesTQ.rows on (_.id === _.nodeId) joinLeft PropsTQ.rows on (_._2.map(_.msId) === _.msId)
    //} yield (d, m, p)
    var q = NodesTQ.getAllNodes(orgid)

    // add filters
    params.get("idfilter").foreach(id => { if (id.contains("%")) q = q.filter(_.id like id) else q = q.filter(_.id === id) })
    params.get("name").foreach(name => { if (name.contains("%")) q = q.filter(_.name like name) else q = q.filter(_.name === name) })
    params.get("owner").foreach(owner => { if (owner.contains("%")) q = q.filter(_.owner like owner) else q = q.filter(_.owner === owner) })

    db.run(q.result).map({ list =>
      logger.debug("GET /orgs/"+orgid+"/nodes result size: "+list.size)
      val nodes = NodesTQ.parseJoin(superUser, list)
      if (nodes.nonEmpty) resp.setStatus(HttpCode.OK)
      else resp.setStatus(HttpCode.NOT_FOUND)
      GetNodesResponse(nodes, 0)
    })
    // } catch { case e: Exception => halt(HttpCode.INTERNAL_ERROR, ApiResponse(ApiResponseType.INTERNAL_ERROR, "Oops! Somthing unexpected happened: "+e)) }
  })

  /* ====== GET /orgs/{orgid}/nodes/{id} ================================ */
  val getOneNode =
    (apiOperation[GetNodesResponse]("getOneNode")
      summary("Returns a node")
      description("""Returns the node (RPi) with the specified id in the exchange DB. Can be run by that node, a user, or an agbot.""")
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node."), paramType=ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("attribute", DataType.String, Option[String]("Which attribute value should be returned. Only 1 attribute can be specified, and it must be 1 of the direct attributes of the node resource (not of the services). If not specified, the entire node resource (including services) will be returned."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  get("/orgs/:orgid/nodes/:id", operation(getOneNode)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    val ident = authenticate().authorizeTo(TNode(id),Access.READ)
    val isSuperUser = ident.isSuperUser
    val resp = response
    params.get("attribute") match {
      case Some(attribute) => ; // Only returning 1 attr of the node
        val q = NodesTQ.getAttribute(id, attribute)
        if (q == null) halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Node attribute name '"+attribute+"' is not an attribute of the node resource."))
        db.run(q.result).map({ list =>
          logger.debug("GET /orgs/"+orgid+"/nodes/"+bareId+" attribute result: "+list.size)
          if (list.nonEmpty) {
            resp.setStatus(HttpCode.OK)
            GetNodeAttributeResponse(attribute, list.head.toString)
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            ApiResponse(ApiResponseType.NOT_FOUND, "not found")     // validateAccessToNode() will return ApiResponseType.NOT_FOUND to the client so do that here for consistency
          }
        })

      case None => ;  // Return the whole node
        val q = NodesTQ.getNode(id)
        db.run(q.result).map({ list =>
          logger.debug("GET /orgs/"+orgid+"/nodes/"+bareId+" result: "+list.size)
          if (list.nonEmpty) {
            val nodes = NodesTQ.parseJoin(isSuperUser, list)
            resp.setStatus(HttpCode.OK)
            GetNodesResponse(nodes, 0)
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            ApiResponse(ApiResponseType.NOT_FOUND, "not found")     // validateAccessToNode() will return ApiResponseType.NOT_FOUND to the client so do that here for consistency
          }
        })
    }
  })

  // ======== POST /org/{orgid}/patterns/{pat-id}/search ========================
  val postPatternSearch =
    (apiOperation[PostPatternSearchResponse]("postPatternSearch")
      summary("Returns matching nodes of a particular pattern")
      description """Returns the matching nodes that are using this pattern and do not already have an agreement for the specified service. Can be run by a user or agbot (but not a node). The **request body** structure:

```
{
  "serviceUrl": "myorg/mydomain.com.sdr",   // The service that the node does not have an agreement with yet. Composite svc url (org/svc)
  "nodeOrgids": [ "org1", "org2", "..." ],   // if not specified, defaults to the same org the pattern is in
  "secondsStale": 60,     // max number of seconds since the exchange has heard from the node, 0 if you do not care
  "startIndex": 0,    // for pagination, ignored right now
  "numEntries": 0    // ignored right now
}
```"""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("pattern", DataType.String, Option[String]("Pattern id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("Username of exchange user, or ID of an agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("token", DataType.String, Option[String]("Password of exchange user, or token of the agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PostPatternSearchRequest],
          Option[String]("Search criteria to find matching nodes in the exchange. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
      )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val postPatternSearch2 = (apiOperation[PostPatternSearchRequest]("postPatternSearch2") summary("a") description("a"))

  /** Normally called by the agbot to search for available nodes. */
  post("/orgs/:orgid/patterns/:pattern/search", operation(postPatternSearch)) ({
    val orgid = params("orgid")
    val pattern = params("pattern")
    val compositePat = OrgAndId(orgid,pattern).toString
    authenticate().authorizeTo(TNode(OrgAndId(orgid,"*").toString),Access.READ)
    val searchProps = try { parse(request.body).extract[PostPatternSearchRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    searchProps.validate()
    val nodeOrgids = searchProps.nodeOrgids.getOrElse(List(orgid)).toSet
    logger.debug("POST /orgs/"+orgid+"/patterns/"+pattern+"/search criteria: "+searchProps.toString)
    val searchSvcUrl = searchProps.serviceUrl   // this now is a composite value (org/url), but plain url is supported for backward compat
    val resp = response
    /*
      Narrow down the db query results as much as possible by joining the Nodes and NodeAgreements tables and filtering.
      In english, the join gets: n.id, n.msgEndPoint, n.publicKey, a.serviceUrl, a.state
      The filters are: n is in the given list of node orgs, n.pattern==ourpattern, the node is not stale, there is an agreement for this node (the filter a.state=="" is applied later in our code below)
      Then we have to go thru all of the results and find nodes that do NOT have an agreement for searchSvcUrl.
      Note about Slick usage: joinLeft returns node rows even if they don't have any agreements (which means the agreement cols are Option() )
    */
    val oldestTime = if (searchProps.secondsStale > 0) ApiTime.pastUTC(searchProps.secondsStale) else ApiTime.beginningUTC
    val q =
      for {
        (n, a) <- NodesTQ.rows.filter(_.orgid inSet(nodeOrgids)).filter(_.pattern === compositePat).filter(_.publicKey =!= "").filter(_.lastHeartbeat >= oldestTime) joinLeft NodeAgreementsTQ.rows on (_.id === _.nodeId)
      } yield (n.id, n.msgEndPoint, n.publicKey, a.map(_.agrSvcUrl), a.map(_.state))

    def isEqualUrl(agrSvcUrl: String, searchSvcUrl: String): Boolean = {
      if (agrSvcUrl == searchSvcUrl) return true    // this is the relevant check when both agbot and agent are recent enough to use composite urls (org/org)
      // Assume searchSvcUrl is the new composite format (because the agbot is at least as high version as the agent) and strip off the org
      val reg = """^\S+?/(\S+)$""".r
      searchSvcUrl match {
        case reg(url) => return agrSvcUrl == url
        case _ => return false    // searchSvcUrl was not composite, so the urls are not equal
      }
    }

    db.run(PatternsTQ.getServices(compositePat).result.flatMap({ list =>
      logger.debug("POST /orgs/"+orgid+"/patterns/"+pattern+"/search getServices size: "+list.size)
      logger.trace("POST /orgs/"+orgid+"/patterns/"+pattern+"/search: looking for '"+searchSvcUrl+"', searching getServices: "+list.toString())
      if (list.nonEmpty) {
        val services = PatternsTQ.getServicesFromString(list.head)    // we should have found only 1 pattern services string, now parse it to get service list
        var found = false
        breakable { for ( svc <- services) {
          if (svc.serviceOrgid+"/"+svc.serviceUrl == searchSvcUrl || svc.serviceUrl == searchSvcUrl) {
            found = true
            break
          }
        } }
        if (found) q.result.asTry
        else DBIO.failed(new Throwable("the serviceUrl '"+searchSvcUrl+"' specified in search body does not exist in pattern '"+compositePat+"'")).asTry
      }
      else DBIO.failed(new Throwable("pattern '"+compositePat+"' not found")).asTry
    })).map({ xs =>
      logger.debug("POST /orgs/"+orgid+"/patterns/"+pattern+"/search result size: "+xs.getOrElse(Vector()).size)
      //logger.trace("POST /orgs/"+orgid+"/patterns/"+pattern+"/search result: "+xs.toString)
      xs match {
        case Success(list) => if (list.nonEmpty) {
            // Go thru the rows and build a hash of the nodes that do NOT have an agreement for our service
            val nodeHash = new MutableHashMap[String,PatternSearchHashElement]     // key is node id, value noAgreementYet which is true if so far we haven't hit an agreement for our service for this node
            for ( (nodeid, msgEndPoint, publicKey, agrSvcUrlOpt, stateOpt) <- list ) {
              //logger.trace("nodeid: "+nodeid+", agrSvcUrlOpt: "+agrSvcUrlOpt.getOrElse("")+", searchSvcUrl: "+searchSvcUrl+", stateOpt: "+stateOpt.getOrElse(""))
              nodeHash.get(nodeid) match {
                case Some(_) => if (isEqualUrl(agrSvcUrlOpt.getOrElse(""), searchSvcUrl) && stateOpt.getOrElse("") != "") { /*logger.trace("setting to false");*/ nodeHash.put(nodeid, PatternSearchHashElement(msgEndPoint, publicKey, noAgreementYet = false)) }  // this is no longer a candidate
                case None => val noAgr = if (isEqualUrl(agrSvcUrlOpt.getOrElse(""), searchSvcUrl) && stateOpt.getOrElse("") != "") false else true
                  nodeHash.put(nodeid, PatternSearchHashElement(msgEndPoint, publicKey, noAgr))   // this node nodeid not in the hash yet, add it
              }
            }
            // Convert our hash to the list response of the rest api
            //val respList = list.map( x => PatternNodeResponse(x._1, x._2, x._3)).toList
            val respList = new ListBuffer[PatternNodeResponse]
            for ( (k, v) <- nodeHash) if (v.noAgreementYet) respList += PatternNodeResponse(k, v.msgEndPoint, v.publicKey)
            if (respList.nonEmpty) resp.setStatus(HttpCode.POST_OK)
            else resp.setStatus(HttpCode.NOT_FOUND)
            PostPatternSearchResponse(respList.toList, 0)
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            PostPatternSearchResponse(List[PatternNodeResponse](), 0)
          }
        case Failure(t) => resp.setStatus(HttpCode.BAD_INPUT)
          ApiResponse(ApiResponseType.BAD_INPUT, "invalid input: "+t.getMessage)
      }
    })
  })

  /** From the given db joined node/agreement rows, build the output node health hash and return it.
     This is shared between POST /org/{orgid}/patterns/{pat-id}/nodehealth and POST /org/{orgid}/search/nodehealth
    */
  def buildNodeHealthHash(list: scala.Seq[(String, String, Option[String], Option[String])]): Map[String,NodeHealthHashElement] = {
    // Go thru the rows and build a hash of the nodes, adding the agreement to its value as we encounter them
    val nodeHash = new MutableHashMap[String,NodeHealthHashElement]     // key is node id, value has lastHeartbeat and the agreements map
    for ( (nodeId, lastHeartbeat, agrId, agrLastUpdated) <- list ) {
      nodeHash.get(nodeId) match {
        case Some(nodeElement) => agrId match {    // this node is already in the hash, add the agreement if it's there
          case Some(agId) => nodeElement.agreements = nodeElement.agreements + ((agId, NodeHealthAgreementElement(agrLastUpdated.getOrElse(""))))    // if we are here, lastHeartbeat is already set and the agreement Map is already created
          case None => ;      // no agreement to add to the agreement hash
        }
        case None => agrId match {      // this node id not in the hash yet, add it
          case Some(agId) => nodeHash.put(nodeId, new NodeHealthHashElement(lastHeartbeat, Map(agId -> NodeHealthAgreementElement(agrLastUpdated.getOrElse("")))))
          case None => nodeHash.put(nodeId, new NodeHealthHashElement(lastHeartbeat, Map()))
        }
      }
    }
    return nodeHash.toMap
  }

  // ======== POST /org/{orgid}/patterns/{pat-id}/nodehealth ========================
  val postPatternNodeHealth =
    (apiOperation[PostNodeHealthResponse]("postPatternNodeHealth")
      summary("Returns agreement health of nodes of a particular pattern")
      description """Returns the lastHeartbeat and agreement times for all nodes that are this pattern and have changed since the specified lastTime. Can be run by a user or agbot (but not a node). The **request body** structure:

```
{
  "lastTime": "2017-09-28T13:51:36.629Z[UTC]",   // only return nodes that have changed since this time, empty string returns all
  "nodeOrgids": [ "org1", "org2", "..." ]   // if not specified, defaults to the same org the pattern is in
}
```"""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("pattern", DataType.String, Option[String]("Pattern id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("Username of exchange user, or ID of an agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("token", DataType.String, Option[String]("Password of exchange user, or token of the agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PostNodeHealthRequest],
          Option[String]("Search criteria to find matching nodes in the exchange. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
      )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val postPatternNodeHealth2 = (apiOperation[PostNodeHealthRequest]("postPatternNodeHealth2") summary("a") description("a"))

  /** Called by the agbot to get recent info about nodes with this pattern (and the agreements the node has). */
  post("/orgs/:orgid/patterns/:pattern/nodehealth", operation(postPatternNodeHealth)) ({
    val orgid = params("orgid")
    val pattern = params("pattern")
    val compositePat = OrgAndId(orgid,pattern).toString
    authenticate().authorizeTo(TNode(OrgAndId(orgid,"*").toString),Access.READ)
    val searchProps = try { parse(request.body).extract[PostNodeHealthRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    searchProps.validate()
    val nodeOrgids = searchProps.nodeOrgids.getOrElse(List(orgid)).toSet
    logger.debug("POST /orgs/"+orgid+"/patterns/"+pattern+"/nodehealth criteria: "+searchProps.toString)
    val resp = response
    /*
      Join nodes and agreements and return: n.id, n.lastHeartbeat, a.id, a.lastUpdated.
      The filter is: n.pattern==ourpattern && n.lastHeartbeat>=lastTime
      Note about Slick usage: joinLeft returns node rows even if they don't have any agreements (which means the agreement cols are Option() )
    */
    val lastTime = if (searchProps.lastTime != "") searchProps.lastTime else ApiTime.beginningUTC
    val q = for {
      (n, a) <- NodesTQ.rows.filter(_.orgid inSet(nodeOrgids)).filter(_.pattern === compositePat).filter(_.lastHeartbeat >= lastTime) joinLeft NodeAgreementsTQ.rows on (_.id === _.nodeId)
    } yield (n.id, n.lastHeartbeat, a.map(_.agId), a.map(_.lastUpdated))

    db.run(q.result).map({ list =>
      logger.debug("POST /orgs/"+orgid+"/patterns/"+pattern+"/nodehealth result size: "+list.size)
      //logger.trace("POST /orgs/"+orgid+"/patterns/"+pattern+"/nodehealth result: "+list.toString)
      if (list.nonEmpty) {
        resp.setStatus(HttpCode.POST_OK)
        PostNodeHealthResponse(buildNodeHealthHash(list))
      }
      else {
        resp.setStatus(HttpCode.NOT_FOUND)
        PostNodeHealthResponse(Map[String,NodeHealthHashElement]())
      }
    })
  })

  // ======== POST /orgs/{orgid}/search/nodes ========================
  val postSearchNodes =
    (apiOperation[PostSearchNodesResponse]("postSearchNodes")
      summary("Returns matching nodes")
      description """Based on the input selection criteria, returns the matching nodes (RPis) in the exchange DB. Can be run by a user or agbot (but not a node). The **request body** structure:

```
{
  "desiredServices": [    // list of data services you are interested in
    {
      "url": "myorg/mydomain.com.rtlsdr",    // composite svc identifier (org/url)
      "properties": [    // list of properties to match specific nodes/services
        {
          "name": "arch",         // typical property names are: arch, version, dataVerification, memory
          "value": "arm",         // should always be a string (even for boolean and int). Use "*" for wildcard
          "propType": "string",   // valid types: string, list, version, boolean, int, or wildcard
          "op": "="               // =, <=, >=, or in
        }
      ]
    }
  ],
  "secondsStale": 60,     // max number of seconds since the exchange has heard from the node, 0 if you do not care
  "startIndex": 0,    // for pagination, ignored right now
  "numEntries": 0    // ignored right now
}
```"""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("Username of exchange user, or ID of an agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("token", DataType.String, Option[String]("Password of exchange user, or token of the agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PostSearchNodesRequest],
          Option[String]("Search criteria to find matching nodes in the exchange. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
      )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val postSearchNodes2 = (apiOperation[PostSearchNodesRequest]("postSearchNodes2") summary("a") description("a"))

  /** Normally called by the agbot to search for available nodes. */
  post("/orgs/:orgid/search/nodes", operation(postSearchNodes)) ({
    val orgid = params("orgid")
    authenticate().authorizeTo(TNode(OrgAndId(orgid,"*").toString),Access.READ)
    val searchProps = try { parse(request.body).extract[PostSearchNodesRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    searchProps.validate()
    logger.debug("POST /orgs/"+orgid+"/search/nodes criteria: "+searchProps.desiredServices.toString)
    val resp = response
    // Narrow down the db query results as much as possible with db selects, then searchProps.matches will do the rest.
    var q = NodesTQ.getNonPatternNodes(orgid).filter(_.publicKey =!= "")
    // Also filter out nodes that are too stale (have not heartbeated recently)
    if (searchProps.secondsStale > 0) q = q.filter(_.lastHeartbeat >= ApiTime.pastUTC(searchProps.secondsStale))

    var agHash: AgreementsHash = null
    db.run(NodeAgreementsTQ.getAgreementsWithState(orgid).result.flatMap({ agList =>
      logger.debug("POST /orgs/" + orgid + "/search/nodes aglist result size: " + agList.size)
      //logger.trace("POST /orgs/" + orgid + "/search/nodes aglist result: " + agList.toString)
      agHash = new AgreementsHash(agList)
      q.result // queue up our node query next
    })).map({ list =>
      logger.debug("POST /orgs/" + orgid + "/search/nodes result size: " + list.size)
      // logger.trace("POST /orgs/"+orgid+"/search/nodes result: "+list.toString)
      // logger.trace("POST /orgs/"+orgid+"/search/nodes agHash: "+agHash.agHash.toString)
      if (list.nonEmpty) resp.setStatus(HttpCode.POST_OK) //todo: this check only catches if there are no nodes at all, not the case in which there are some nodes, but they do not have the right services
      else resp.setStatus(HttpCode.NOT_FOUND)
      val nodes = new MutableHashMap[String,Node]    // the key is node id
      if (list.nonEmpty) for (a <- list) nodes.put(a.id, a.toNode(false))
      searchProps.matches(nodes.toMap, agHash)
    })
  })

  // ======== POST /org/{orgid}/search/nodehealth ========================
  val postSearchNodeHealth =
    (apiOperation[PostNodeHealthResponse]("postSearchNodeHealth")
      summary("Returns agreement health of nodes with no pattern")
      description """Returns the lastHeartbeat and agreement times for all nodes in this org that do not have a pattern and have changed since the specified lastTime. Can be run by a user or agbot (but not a node). The **request body** structure:

```
{
  "lastTime": "2017-09-28T13:51:36.629Z[UTC]"   // only return nodes that have changed since this time, empty string returns all
}
```"""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("Username of exchange user, or ID of an agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("token", DataType.String, Option[String]("Password of exchange user, or token of the agbot. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PostNodeHealthRequest],
          Option[String]("Search criteria to find matching nodes in the exchange. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
      )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val postSearchNodeHealth2 = (apiOperation[PostNodeHealthRequest]("postSearchNodeHealth2") summary("a") description("a"))

  /** Called by the agbot to get recent info about nodes with no pattern (and the agreements the node has). */
  post("/orgs/:orgid/search/nodehealth", operation(postSearchNodeHealth)) ({
    val orgid = params("orgid")
    //val pattern = params("patid")
    //val compositePat = OrgAndId(orgid,pattern).toString
    authenticate().authorizeTo(TNode(OrgAndId(orgid,"*").toString),Access.READ)
    val searchProps = try { parse(request.body).extract[PostNodeHealthRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    searchProps.validate()
    logger.debug("POST /orgs/"+orgid+"/search/nodehealth criteria: "+searchProps.toString)
    val resp = response
    /*
      Join nodes and agreements and return: n.id, n.lastHeartbeat, a.id, a.lastUpdated.
      The filter is: n.pattern=="" && n.lastHeartbeat>=lastTime
      Note about Slick usage: joinLeft returns node rows even if they don't have any agreements (which means the agreement cols are Option() )
    */
    val lastTime = if (searchProps.lastTime != "") searchProps.lastTime else ApiTime.beginningUTC
    val q = for {
      (n, a) <- NodesTQ.rows.filter(_.orgid === orgid).filter(_.pattern === "").filter(_.lastHeartbeat >= lastTime) joinLeft NodeAgreementsTQ.rows on (_.id === _.nodeId)
    } yield (n.id, n.lastHeartbeat, a.map(_.agId), a.map(_.lastUpdated))

    db.run(q.result).map({ list =>
      logger.debug("POST /orgs/"+orgid+"/search/nodehealth result size: "+list.size)
      //logger.trace("POST /orgs/"+orgid+"/patterns/"+pattern+"/nodehealth result: "+list.toString)
      if (list.nonEmpty) {
        resp.setStatus(HttpCode.POST_OK)
        PostNodeHealthResponse(buildNodeHealthHash(list))
      }
      else {
        resp.setStatus(HttpCode.NOT_FOUND)
        PostNodeHealthResponse(Map[String,NodeHealthHashElement]())
      }
    })
  })

  // =========== PUT /orgs/{orgid}/nodes/{id} ===============================
  val putNodes =
    (apiOperation[Map[String,String]]("putNodes")
      summary "Adds/updates a node"
      description """Adds a new edge node to the exchange DB, or updates an existing node. This must be called by the user to add a node, and then can be called by that user or node to update itself. The **request body** structure:

```
{
  "token": "abc",       // node token, set by user when adding this node.
  "name": "rpi3",         // node name that you pick
  "pattern": "myorg/mypattern",      // (optional) points to a pattern resource that defines what services should be deployed to this type of node
  "registeredServices": [    // list of data services you want to make available
    {
      "url": "IBM/github.com.open-horizon.examples.cpu",
      "numAgreements": 1,       // for now always set this to 1
      "policy": "{...}"     // the service policy file content as a json string blob
      "properties": [    // list of properties to help agbots search for this, or requirements on the agbot
        {
          "name": "arch",         // must at least include arch and version properties
          "value": "arm",         // should always be a string (even for boolean and int). Use "*" for wildcard
          "propType": "string",   // valid types: string, list, version, boolean, int, or wildcard
          "op": "="               // =, greater-than-or-equal-symbols, less-than-or-equal-symbols, or in (must use the same op as the agbot search)
        }
      ]
    }
  ],
  "msgEndPoint": "",    // not currently used, but may be in the future. Leave empty or omit to use the built-in Exchange msg service
  "softwareVersions": {"horizon": "1.2.3"},      // various software versions on the node, can omit
  "publicKey": "ABCDEF"      // used by agbots to encrypt msgs sent to this node using the built-in Exchange msg service
}
```"""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node to be added/updated."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PutNodesRequest],
          Option[String]("Node object that needs to be added to, or updated in, the exchange. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
        )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val putNodes2 = (apiOperation[PutNodesRequest]("putNodes2") summary("a") description("a"))  // for some bizarre reason, the PutNodesRequest class has to be used in apiOperation() for it to be recognized in the body Parameter above

  put("/orgs/:orgid/nodes/:id", operation(putNodes)) ({
    // consider writing a customer deserializer that will do error checking on the body, see: https://gist.github.com/fehguy/4191861#file-gistfile1-scala-L74
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    val ident = authenticate().authorizeTo(TNode(id),Access.WRITE)
    val node = try { parse(request.body).extract[PutNodesRequest] }
    catch {
      case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e))
    }
    node.validate()
    val owner = ident match { case IUser(creds) => creds.id; case _ => "" }
    val resp = response
    val patValidateAction = if (node.pattern != "") PatternsTQ.getPattern(node.pattern).length.result else DBIO.successful(1)
    db.run(patValidateAction.asTry.flatMap({ xs =>
      logger.debug("PUT /orgs/"+orgid+"/nodes/"+bareId+" pattern validation: "+xs.toString)
      xs match {
        case Success(num) => if (num > 0) NodesTQ.getNumOwned(owner).result.asTry
          else DBIO.failed(new Throwable("the referenced pattern does not exist in the exchange")).asTry
        case Failure(t) => DBIO.failed(new Throwable(t.getMessage)).asTry
      }
    }).flatMap({ xs =>
      logger.debug("PUT /orgs/"+orgid+"/nodes/"+bareId+" num owned: "+xs)
      xs match {
        case Success(numOwned) => val maxNodes = ExchConfig.getInt("api.limits.maxNodes")
          if (maxNodes == 0 || numOwned <= maxNodes || owner == "") {    // when owner=="" we know it is only an update, otherwise we are not sure, but if they are already over the limit, stop them anyway
            val action = if (owner == "") node.getDbUpdate(id, orgid, owner) else node.getDbUpsert(id, orgid, owner)
            action.transactionally.asTry
          }
          else DBIO.failed(new Throwable("Access Denied: you are over the limit of "+maxNodes+ " nodes")).asTry
        case Failure(t) => DBIO.failed(new Throwable(t.getMessage)).asTry
      }
    })).map({ xs =>
      logger.debug("PUT /orgs/"+orgid+"/nodes/"+bareId+" result: "+xs.toString)
      xs match {
        case Success(_) => AuthCache.nodes.putBoth(Creds(id,node.token),owner)    // the token passed in to the cache should be the non-hashed one
          resp.setStatus(HttpCode.PUT_OK)
          ApiResponse(ApiResponseType.OK, "node added or updated")
        case Failure(t) => if (t.getMessage.startsWith("Access Denied:")) {
            resp.setStatus(HttpCode.ACCESS_DENIED)
            ApiResponse(ApiResponseType.ACCESS_DENIED, "node '"+id+"' not inserted or updated: "+t.getMessage)
          } else {
            resp.setStatus(HttpCode.BAD_INPUT)
            ApiResponse(ApiResponseType.BAD_INPUT, "node '"+id+"' not inserted or updated: "+t.getMessage)
          }
      }
    })
  })

  // =========== PATCH /orgs/{orgid}/nodes/{id} ===============================
  val patchNodes =
    (apiOperation[Map[String,String]]("patchNodes")
      summary "Updates 1 attribute of a node"
      description """Updates some attributes of a node (RPi) in the exchange DB. This can be called by the user or the node."""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node to be updated."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PatchNodesRequest],
          Option[String]("Node object that contains attributes to updated in, the exchange. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
        )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val patchNodes2 = (apiOperation[PatchNodesRequest]("patchNodes2") summary("a") description("a"))  // for some bizarre reason, the PatchNodesRequest class has to be used in apiOperation() for it to be recognized in the body Parameter above

  patch("/orgs/:orgid/nodes/:id", operation(patchNodes)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val node = try { parse(request.body).extract[PatchNodesRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    //logger.trace("PATCH /orgs/"+orgid+"/nodes/"+bareId+" input: "+node.toString)
    val resp = response
    val (action, attrName) = node.getDbUpdate(id)
    if (action == null) halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "no valid node attribute specified"))
    val patValidateAction = if (attrName == "pattern" && node.pattern.get != "") PatternsTQ.getPattern(node.pattern.get).length.result else DBIO.successful(1)
    db.run(patValidateAction.asTry.flatMap({ xs =>
      logger.debug("PATCH /orgs/"+orgid+"/nodes/"+bareId+" pattern validation: "+xs.toString)
      xs match {
        case Success(num) => if (num > 0) action.transactionally.asTry
          else DBIO.failed(new Throwable("the referenced pattern does not exist in the exchange")).asTry
        case Failure(t) => DBIO.failed(new Throwable(t.getMessage)).asTry
      }
    })).map({ xs =>
      logger.debug("PATCH /orgs/"+orgid+"/nodes/"+bareId+" result: "+xs.toString)
      xs match {
        case Success(v) => try {
            val numUpdated = v.toString.toInt     // v comes to us as type Any
            if (numUpdated > 0) {        // there were no db errors, but determine if it actually found it or not
              node.token match { case Some(tok) if (tok != "") => AuthCache.nodes.put(Creds(id, tok)); case _ => ; }    // the token passed in to the cache should be the non-hashed one. We do not need to run putOwner because patch does not change the owner
              resp.setStatus(HttpCode.PUT_OK)
              ApiResponse(ApiResponseType.OK, "attribute '"+attrName+"' of node '"+id+"' updated")
            } else {
              resp.setStatus(HttpCode.NOT_FOUND)
              ApiResponse(ApiResponseType.NOT_FOUND, "node '"+id+"' not found")
            }
          } catch { case e: Exception => resp.setStatus(HttpCode.INTERNAL_ERROR); ApiResponse(ApiResponseType.INTERNAL_ERROR, "Unexpected result from update: "+e) }
        case Failure(t) => resp.setStatus(HttpCode.BAD_INPUT)
          ApiResponse(ApiResponseType.BAD_INPUT, "node '"+id+"' not inserted or updated: "+t.getMessage)
      }
    })
  })

  // =========== POST /orgs/{orgid}/nodes/{id}/services_configstate ===============================
  val postNodesConfigstate =
    (apiOperation[ApiResponse]("postNodesConfigstate")
      summary "Changes config state of registered services"
      description """Suspends (or resumes) 1 or more services on this edge node. Can be run by the node owner or the node. The **request body** structure:

```
{
  "org": "myorg",    // the org of services to be modified, or empty string for all orgs
  "url": "myserviceurl"       // the url of services to be modified, or empty string for all urls
  "configState": "suspended"   // or "active"
}
```
      """
      parameters(
      Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
      Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node to be modified."), paramType = ParamType.Path),
      Parameter("body", DataType[PostNodeConfigStateRequest],
        Option[String]("Service selection and desired state. See details in the Implementation Notes above."),
        paramType = ParamType.Body)
    )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val postNodesConfigState2 = (apiOperation[PostNodeConfigStateRequest]("postNodesConfigstate2") summary("a") description("a"))

  post("/orgs/:orgid/nodes/:id/services_configstate", operation(postNodesConfigstate)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val nodeId = OrgAndId(orgid,bareId).toString
    val configStateReq = try { parse(request.body).extract[PostNodeConfigStateRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    configStateReq.validate()
    val resp = response

    db.run(NodesTQ.getRegisteredServices(nodeId).result.asTry.flatMap({ xs =>
      logger.debug("POST /orgs/"+orgid+"/nodes/"+bareId+"/configstate result: "+xs.toString)
      xs match {
        case Success(v) => if (v.nonEmpty) configStateReq.getDbUpdate(v.head, nodeId).asTry   // pass the update action to the next step
          else DBIO.failed(new Throwable("Invalid Input: node "+nodeId+" not found")).asTry    // it seems this returns success even when the node is not found
        case Failure(t) => DBIO.failed(t).asTry       // rethrow the error to the next step. Is this necessary, or will flatMap do that automatically?
      }

    })).map({ xs =>
      logger.debug("POST /orgs/"+orgid+"/nodes/"+bareId+"/configstate write row result: "+xs.toString)
      xs match {
        case Success(i) => //try {     // i comes to us as type Any
          if (i.toString.toInt > 0) {        // there were no db errors, but determine if it actually found it or not
            resp.setStatus(HttpCode.PUT_OK)
            ApiResponse(ApiResponseType.OK, "registeredServices of node '"+nodeId+"' updated")
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            ApiResponse(ApiResponseType.NOT_FOUND, "node '"+nodeId+"' not found")
          }
          //} catch { case e: Exception => resp.setStatus(HttpCode.INTERNAL_ERROR); ApiResponse(ApiResponseType.INTERNAL_ERROR, "Unexpected result from update: "+e) }
        case Failure(t) => resp.setStatus(HttpCode.BAD_INPUT)
          ApiResponse(ApiResponseType.BAD_INPUT, "node '"+nodeId+"' not inserted or updated: "+t.getMessage)
      }
    })

  })

  // =========== DELETE /orgs/{orgid}/nodes/{id} ===============================
  val deleteNodes =
    (apiOperation[ApiResponse]("deleteNodes")
      summary "Deletes a node"
      description "Deletes a node (RPi) from the exchange DB, and deletes the agreements stored for this node (but does not actually cancel the agreements between the node and agbots). Can be run by the owning user or the node."
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node to be deleted."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.DELETED,"deleted"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  delete("/orgs/:orgid/nodes/:id", operation(deleteNodes)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    // remove does *not* throw an exception if the key does not exist
    val resp = response
    db.run(NodesTQ.getNode(id).delete.transactionally.asTry).map({ xs =>
      logger.debug("DELETE /orgs/"+orgid+"/nodes/"+bareId+" result: "+xs.toString)
      xs match {
        case Success(v) => if (v > 0) {        // there were no db errors, but determine if it actually found it or not
            AuthCache.nodes.removeBoth(id)
            resp.setStatus(HttpCode.DELETED)
            ApiResponse(ApiResponseType.OK, "node deleted")
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            ApiResponse(ApiResponseType.NOT_FOUND, "node '"+id+"' not found")
          }
        case Failure(t) => resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "node '"+id+"' not deleted: "+t.toString)
        }
    })
  })

  // =========== POST /orgs/{orgid}/nodes/{id}/heartbeat ===============================
  val postNodesHeartbeat =
    (apiOperation[ApiResponse]("postNodesHeartbeat")
      summary "Tells the exchange this node is still operating"
      description "Lets the exchange know this node is still active so it is still a candidate for contracting. Can be run by the owning user or the node."
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node to be updated."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"post ok"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  post("/orgs/:orgid/nodes/:id/heartbeat", operation(postNodesHeartbeat)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val resp = response
    db.run(NodesTQ.getLastHeartbeat(id).update(ApiTime.nowUTC).asTry).map({ xs =>
      logger.debug("POST /orgs/"+orgid+"/nodes/"+bareId+"/heartbeat result: "+xs.toString)
      xs match {
        case Success(v) => if (v > 0) {       // there were no db errors, but determine if it actually found it or not
              resp.setStatus(HttpCode.POST_OK)
              ApiResponse(ApiResponseType.OK, "node updated")
            } else {
              resp.setStatus(HttpCode.NOT_FOUND)
              ApiResponse(ApiResponseType.NOT_FOUND, "node '"+id+"' not found")
            }
        case Failure(t) => resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "node '"+id+"' not updated: "+t.toString)
        }
    })
  })


  /* ====== GET /orgs/{orgid}/nodes/{id}/status ================================ */
  val getNodeStatus =
    (apiOperation[NodeStatus]("getNodeStatus")
      summary("Returns the node status")
      description("""Returns the node run time status, for example service container status. Can be run by a user or the node.""")
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node."), paramType=ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"post ok"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  get("/orgs/:orgid/nodes/:id/status", operation(getNodeStatus)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.READ)
    val resp = response
      db.run(NodeStatusTQ.getNodeStatus(id).result).map({ list =>
        logger.debug("GET /orgs/"+orgid+"/nodes/"+bareId+"/status result size: "+list.size)
        if (list.nonEmpty) {
          resp.setStatus(HttpCode.OK)
          list.head.toNodeStatus
        }
        else resp.setStatus(HttpCode.NOT_FOUND)
      })
  })

  // =========== PUT /orgs/{orgid}/nodes/{id}/status ===============================
  val putNodeStatus =
    (apiOperation[ApiResponse]("putNodeStatus")
      summary "Adds/updates the node status"
      description """Adds or updates the run time status of a node. This is called by the node or owning user. The **request body** structure:

```
{
  "connectivity": {
     "firmware.bluehorizon.network": true,
      "images.bluehorizon.network": true
   },
  "services": [
    {
      "agreementId": "78d7912aafb6c11b7a776f77d958519a6dc718b9bd3da36a1442ebb18fe9da30",
      "serviceUrl":"mydomain.com.location",
      "orgid":"ling.com",
      "version":"1.2",
      "arch":"amd64",
      "containers": [
        {
          "name": "/dc23c045eb64e1637d027c4b0236512e89b2fddd3f06290c7b2354421d9d8e0d-location",
          "image": "summit.hovitos.engineering/x86/location:v1.2",
          "created": 1506086099,
          "state": "running"
        }
      ]
    }
  ]
}
```"""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node wanting to add/update this status."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PutNodeStatusRequest],
          Option[String]("Status object add or update. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
      )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val putNodeStatus2 = (apiOperation[PutNodeStatusRequest]("putNodeStatus2") summary("a") description("a"))  // for some bizarre reason, the PutNodeStatusRequest class has to be used in apiOperation() for it to be recognized in the body Parameter above

  put("/orgs/:orgid/nodes/:id/status", operation(putNodeStatus)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val status = try { parse(request.body).extract[PutNodeStatusRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    status.validate()
    val resp = response
    db.run(status.toNodeStatusRow(id).upsert.asTry).map({ xs =>
      logger.debug("PUT /orgs/"+orgid+"/nodes/"+bareId+"/status result: "+xs.toString)
      xs match {
        case Success(_) => resp.setStatus(HttpCode.PUT_OK)
          ApiResponse(ApiResponseType.OK, "status added or updated")
        case Failure(t) => if (t.getMessage.startsWith("Access Denied:")) {
          resp.setStatus(HttpCode.ACCESS_DENIED)
          ApiResponse(ApiResponseType.ACCESS_DENIED, "status for node '"+id+"' not inserted or updated: "+t.getMessage)
        } else {
          resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "status for node '"+id+"' not inserted or updated: "+t.toString)
        }
      }
    })
  })

  // =========== DELETE /orgs/{orgid}/nodes/{id}/status ===============================
  val deleteNodeStatus =
    (apiOperation[ApiResponse]("deleteNodeStatus")
      summary "Deletes the status of a node"
      description "Deletes the status of a node from the exchange DB. Can be run by the owning user or the node."
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node for which the status is to be deleted."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
      )
      responseMessages(ResponseMessage(HttpCode.DELETED,"deleted"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  delete("/orgs/:orgid/nodes/:id/status", operation(deleteNodeStatus)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val resp = response
    db.run(NodeStatusTQ.getNodeStatus(id).delete.asTry).map({ xs =>
      logger.debug("DELETE /orgs/"+orgid+"/nodes/"+bareId+"/status result: "+xs.toString)
      xs match {
        case Success(v) => if (v > 0) {        // there were no db errors, but determine if it actually found it or not
          resp.setStatus(HttpCode.DELETED)
          ApiResponse(ApiResponseType.OK, "node status deleted")
        } else {
          resp.setStatus(HttpCode.NOT_FOUND)
          ApiResponse(ApiResponseType.NOT_FOUND, "status for node '"+id+"' not found")
        }
        case Failure(t) => resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "status for node '"+id+"' not deleted: "+t.toString)
      }
    })
  })


  /* ====== GET /orgs/{orgid}/nodes/{id}/agreements ================================ */
  val getNodeAgreements =
    (apiOperation[GetNodeAgreementsResponse]("getNodeAgreements")
      summary("Returns all agreements this node is in")
      description("""Returns all agreements in the exchange DB that this node is part of. Can be run by a user or the node.""")
      parameters(
      Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
      Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node."), paramType=ParamType.Path),
      Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
    )
      )

  get("/orgs/:orgid/nodes/:id/agreements", operation(getNodeAgreements)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.READ)
    val resp = response
    db.run(NodeAgreementsTQ.getAgreements(id).result).map({ list =>
      logger.debug("GET /orgs/"+orgid+"/nodes/"+bareId+"/agreements result size: "+list.size)
      val agreements = new MutableHashMap[String, NodeAgreement]
      if (list.nonEmpty) for (e <- list) { agreements.put(e.agId, e.toNodeAgreement) }
      if (agreements.nonEmpty) resp.setStatus(HttpCode.OK)
      else resp.setStatus(HttpCode.NOT_FOUND)
      GetNodeAgreementsResponse(agreements.toMap, 0)
    })
  })

  /* ====== GET /orgs/{orgid}/nodes/{id}/agreements/{agid} ================================ */
  val getOneNodeAgreement =
    (apiOperation[GetNodeAgreementsResponse]("getOneNodeAgreement")
      summary("Returns an agreement for a node")
      description("""Returns the agreement with the specified agid for the specified node id in the exchange DB. Can be run by a user or the node.""")
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node."), paramType=ParamType.Path),
        Parameter("agid", DataType.String, Option[String]("ID of the agreement."), paramType=ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  get("/orgs/:orgid/nodes/:id/agreements/:agid", operation(getOneNodeAgreement)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    val agId = params("agid")
    authenticate().authorizeTo(TNode(id),Access.READ)
    val resp = response
    db.run(NodeAgreementsTQ.getAgreement(id, agId).result).map({ list =>
      logger.debug("GET /orgs/"+orgid+"/nodes/"+bareId+"/agreements/"+agId+" result: "+list.toString)
      val agreements = new MutableHashMap[String, NodeAgreement]
      if (list.nonEmpty) for (e <- list) { agreements.put(e.agId, e.toNodeAgreement) }
      if (agreements.nonEmpty) resp.setStatus(HttpCode.OK)
      else resp.setStatus(HttpCode.NOT_FOUND)
      GetNodeAgreementsResponse(agreements.toMap, 0)
    })
  })

  // =========== PUT /orgs/{orgid}/nodes/{id}/agreements/{agid} ===============================
  val putNodeAgreement =
    (apiOperation[ApiResponse]("putNodeAgreement")
      summary "Adds/updates an agreement of a node"
      description """Adds a new agreement of a node to the exchange DB, or updates an existing agreement. This is called by the
        node or owning user to give their information about the agreement. The **request body** structure:

```
{
  "services": [          // specify this for CS-type agreements
    {"orgid": "myorg", "url": "mydomain.com.rtlsdr"}
  ],
  "agreementService": {          // specify this for pattern-type agreements
    "orgid": "myorg",     // currently set to the node id, but not used
    "pattern": "myorg/mypattern",    // composite pattern (org/pat)
    "url": "myorg/mydomain.com.sdr"   // composite service url (org/svc)
  },
  "state": "negotiating"    // current agreement state: negotiating, signed, finalized, etc.
}
```"""
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node wanting to add/update this agreement."), paramType = ParamType.Path),
        Parameter("agid", DataType.String, Option[String]("ID of the agreement to be added/updated."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false),
        Parameter("body", DataType[PutNodeAgreementRequest],
          Option[String]("Agreement object that needs to be added to, or updated in, the exchange. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
        )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val putNodeAgreement2 = (apiOperation[PutNodeAgreementRequest]("putAgreement2") summary("a") description("a"))  // for some bizarre reason, the PutAgreementsRequest class has to be used in apiOperation() for it to be recognized in the body Parameter above

  put("/orgs/:orgid/nodes/:id/agreements/:agid", operation(putNodeAgreement)) ({
    //todo: keep a running total of agreements for each MS so we can search quickly for available MSs
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    val agId = params("agid")
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val agreement = try { parse(request.body).extract[PutNodeAgreementRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    agreement.validate()
    val resp = response
    db.run(NodeAgreementsTQ.getNumOwned(id).result.flatMap({ xs =>
      logger.debug("PUT /orgs/"+orgid+"/nodes/"+bareId+"/agreements/"+agId+" num owned: "+xs)
      val numOwned = xs
      val maxAgreements = ExchConfig.getInt("api.limits.maxAgreements")
      if (maxAgreements == 0 || numOwned <= maxAgreements) {    // we are not sure if this is create or update, but if they are already over the limit, stop them anyway
        agreement.toNodeAgreementRow(id, agId).upsert.asTry
      }
      else DBIO.failed(new Throwable("Access Denied: you are over the limit of "+maxAgreements+ " agreements for this node")).asTry
    })).map({ xs =>
      logger.debug("PUT /orgs/"+orgid+"/nodes/"+bareId+"/agreements/"+agId+" result: "+xs.toString)
      xs match {
        case Success(_) => resp.setStatus(HttpCode.PUT_OK)
          ApiResponse(ApiResponseType.OK, "agreement added or updated")
        case Failure(t) => if (t.getMessage.startsWith("Access Denied:")) {
            resp.setStatus(HttpCode.ACCESS_DENIED)
            ApiResponse(ApiResponseType.ACCESS_DENIED, "agreement '"+agId+"' for node '"+id+"' not inserted or updated: "+t.getMessage)
          } else {
            resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "agreement '"+agId+"' for node '"+id+"' not inserted or updated: "+t.toString)
        }
      }
    })
  })

  // =========== DELETE /orgs/{orgid}/nodes/{id}/agreements ===============================
  val deleteNodeAllAgreement =
    (apiOperation[ApiResponse]("deleteNodeAllAgreement")
      summary "Deletes all agreements of a node"
      description "Deletes all of the current agreements of a node from the exchange DB. Can be run by the owning user or the node."
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node for which the agreement is to be deleted."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      )

  delete("/orgs/:orgid/nodes/:id/agreements", operation(deleteNodeAllAgreement)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val resp = response
    db.run(NodeAgreementsTQ.getAgreements(id).delete.asTry).map({ xs =>
      logger.debug("DELETE /orgs/"+orgid+"/nodes/"+bareId+"/agreements result: "+xs.toString)
      xs match {
        case Success(v) => if (v > 0) {        // there were no db errors, but determine if it actually found it or not
            resp.setStatus(HttpCode.DELETED)
            ApiResponse(ApiResponseType.OK, "node agreements deleted")
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            ApiResponse(ApiResponseType.NOT_FOUND, "no agreements for node '"+id+"' found")
          }
        case Failure(t) => resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "agreements for node '"+id+"' not deleted: "+t.toString)
        }
    })
  })

  // =========== DELETE /orgs/{orgid}/nodes/{id}/agreements/{agid} ===============================
  val deleteNodeAgreement =
    (apiOperation[ApiResponse]("deleteNodeAgreement")
      summary "Deletes an agreement of a node"
      description "Deletes an agreement of a node from the exchange DB. Can be run by the owning user or the node."
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node for which the agreement is to be deleted."), paramType = ParamType.Path),
        Parameter("agid", DataType.String, Option[String]("ID of the agreement to be deleted."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.DELETED,"deleted"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  delete("/orgs/:orgid/nodes/:id/agreements/:agid", operation(deleteNodeAgreement)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    val agId = params("agid")
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val resp = response
    db.run(NodeAgreementsTQ.getAgreement(id,agId).delete.asTry).map({ xs =>
      logger.debug("DELETE /orgs/"+orgid+"/nodes/"+bareId+"/agreements/"+agId+" result: "+xs.toString)
      xs match {
        case Success(v) => if (v > 0) {        // there were no db errors, but determine if it actually found it  or not
            resp.setStatus(HttpCode.DELETED)
            ApiResponse(ApiResponseType.OK, "node agreement deleted")
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            ApiResponse(ApiResponseType.NOT_FOUND, "agreement '"+agId+"' for node '"+id+"' not found")
          }
        case Failure(t) => resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "agreement '"+agId+"' for node '"+id+"' not deleted: "+t.toString)
        }
    })
  })

  // =========== POST /orgs/{orgid}/nodes/{id}/msgs ===============================
  val postNodesMsgs =
    (apiOperation[ApiResponse]("postNodesMsgs")
      summary "Sends a msg from an agbot to a node"
      description """Sends a msg from an agbot to a node. The agbot must 1st sign the msg (with its private key) and then encrypt the msg (with the node's public key). Can be run by any agbot. The **request body** structure:

```
{
  "message": "VW1RxzeEwTF0U7S96dIzSBQ/hRjyidqNvBzmMoZUW3hpd3hZDvs",    // msg to be sent to the node
  "ttl": 86400       // time-to-live of this msg, in seconds
}
```
      """
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node to send a msg to."), paramType = ParamType.Path),
        // Agbot id/token must be in the header
        Parameter("body", DataType[PostNodesMsgsRequest],
          Option[String]("Signed/encrypted message to send to the node. See details in the Implementation Notes above."),
          paramType = ParamType.Body)
        )
      responseMessages(ResponseMessage(HttpCode.POST_OK,"created/updated"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.BAD_INPUT,"bad input"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )
  val postNodesMsgs2 = (apiOperation[PostNodesMsgsRequest]("postNodesMsgs2") summary("a") description("a"))

  post("/orgs/:orgid/nodes/:id/msgs", operation(postNodesMsgs)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val nodeId = OrgAndId(orgid,bareId).toString
    val ident = authenticate().authorizeTo(TNode(nodeId),Access.SEND_MSG_TO_NODE)
    val agbotId = ident.creds.id
    val msg = try { parse(request.body).extract[PostNodesMsgsRequest] }
    catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "Error parsing the input body json: "+e)) }    // the specific exception is MappingException
    val resp = response
    // Remove msgs whose TTL is past, then check the mailbox is not full, then get the agbot publicKey, then write the nodemsgs row, all in the same db.run thread
    db.run(NodeMsgsTQ.getMsgsExpired.delete.flatMap({ xs =>
      logger.debug("POST /orgs/"+orgid+"/nodes/"+bareId+"/msgs delete expired result: "+xs.toString)
      NodeMsgsTQ.getNumOwned(nodeId).result
    }).flatMap({ xs =>
      logger.debug("POST /orgs/"+orgid+"/nodes/"+bareId+"/msgs mailbox size: "+xs)
      val mailboxSize = xs
      val maxMessagesInMailbox = ExchConfig.getInt("api.limits.maxMessagesInMailbox")
      if (maxMessagesInMailbox == 0 || mailboxSize < maxMessagesInMailbox) AgbotsTQ.getPublicKey(agbotId).result.asTry
      else DBIO.failed(new Throwable("Access Denied: the message mailbox of "+nodeId+" is full ("+maxMessagesInMailbox+ " messages)")).asTry
    }).flatMap({ xs =>
      logger.debug("POST /orgs/"+orgid+"/nodes/"+bareId+"/msgs agbot publickey result: "+xs.toString)
      xs match {
        case Success(v) => if (v.nonEmpty) {    // it seems this returns success even when the agbot is not found
            val agbotPubKey = v.head
            if (agbotPubKey != "") NodeMsgRow(0, nodeId, agbotId, agbotPubKey, msg.message, ApiTime.nowUTC, ApiTime.futureUTC(msg.ttl)).insert.asTry
            else DBIO.failed(new Throwable("Invalid Input: the message sender must have their public key registered with the Exchange")).asTry
          }
          else DBIO.failed(new Throwable("Invalid Input: agbot "+agbotId+" not found")).asTry
        case Failure(t) => DBIO.failed(t).asTry       // rethrow the error to the next step
      }
    })).map({ xs =>
      logger.debug("POST /orgs/"+orgid+"/nodes/"+bareId+"/msgs write row result: "+xs.toString)
      xs match {
        case Success(v) => resp.setStatus(HttpCode.POST_OK)
          ApiResponse(ApiResponseType.OK, "node msg "+v+" inserted")
        case Failure(t) => if (t.getMessage.startsWith("Invalid Input:")) {
            resp.setStatus(HttpCode.BAD_INPUT)
            ApiResponse(ApiResponseType.BAD_INPUT, "node '"+nodeId+"' msg not inserted: "+t.getMessage)
          } else if (t.getMessage.startsWith("Access Denied:")) {
            resp.setStatus(HttpCode.ACCESS_DENIED)
            ApiResponse(ApiResponseType.ACCESS_DENIED, "node '"+nodeId+"' msg not inserted: "+t.getMessage)
          } else {
            resp.setStatus(HttpCode.INTERNAL_ERROR)
            ApiResponse(ApiResponseType.INTERNAL_ERROR, "node '"+nodeId+"' msg not inserted: "+t.toString)
          }
        }
    })
  })

  /* ====== GET /orgs/{orgid}/nodes/{id}/msgs ================================ */
  val getNodeMsgs =
    (apiOperation[GetNodeMsgsResponse]("getNodeMsgs")
      summary("Returns all msgs sent to this node")
      description("""Returns all msgs that have been sent to this node. They will be returned in the order they were sent. All msgs that have been sent to this node will be returned, unless the node has deleted some, or some are past their TTL. Can be run by a user or the node.""")
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node."), paramType=ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  get("/orgs/:orgid/nodes/:id/msgs", operation(getNodeMsgs)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    authenticate().authorizeTo(TNode(id),Access.READ)
    val resp = response
    // Remove msgs whose TTL is past, and then get the msgs for this node
    db.run(NodeMsgsTQ.getMsgsExpired.delete.flatMap({ xs =>
      logger.debug("GET /orgs/"+orgid+"/nodes/"+bareId+"/msgs delete expired result: "+xs.toString)
      NodeMsgsTQ.getMsgs(id).result
    })).map({ list =>
      logger.debug("GET /orgs/"+orgid+"/nodes/"+bareId+"/msgs result size: "+list.size)
      val listSorted = list.sortWith(_.msgId < _.msgId)
      val msgs = new ListBuffer[NodeMsg]
      if (listSorted.nonEmpty) for (m <- listSorted) { msgs += m.toNodeMsg }
      if (msgs.nonEmpty) resp.setStatus(HttpCode.OK)
      else resp.setStatus(HttpCode.NOT_FOUND)
      GetNodeMsgsResponse(msgs.toList, 0)
    })
  })

  // =========== DELETE /orgs/{orgid}/nodes/{id}/msgs/{msgid} ===============================
  val deleteNodeMsg =
    (apiOperation[ApiResponse]("deleteNodeMsg")
      summary "Deletes an msg of a node"
      description "Deletes an msg that was sent to a node. This should be done by the node after each msg is read. Can be run by the owning user or the node."
      parameters(
        Parameter("orgid", DataType.String, Option[String]("Organization id."), paramType=ParamType.Path),
        Parameter("id", DataType.String, Option[String]("ID (orgid/nodeid) of the node to be deleted."), paramType = ParamType.Path),
        Parameter("msgid", DataType.String, Option[String]("ID of the msg to be deleted."), paramType = ParamType.Path),
        Parameter("token", DataType.String, Option[String]("Token of the node. This parameter can also be passed in the HTTP Header."), paramType=ParamType.Query, required=false)
        )
      responseMessages(ResponseMessage(HttpCode.DELETED,"deleted"), ResponseMessage(HttpCode.BADCREDS,"invalid credentials"), ResponseMessage(HttpCode.ACCESS_DENIED,"access denied"), ResponseMessage(HttpCode.NOT_FOUND,"not found"))
      )

  delete("/orgs/:orgid/nodes/:id/msgs/:msgid", operation(deleteNodeMsg)) ({
    val orgid = params("orgid")
    val bareId = params("id")
    val id = OrgAndId(orgid,bareId).toString
    val msgId = try { params("msgid").toInt } catch { case e: Exception => halt(HttpCode.BAD_INPUT, ApiResponse(ApiResponseType.BAD_INPUT, "msgid must be an integer: "+e)) }    // the specific exception is NumberFormatException
    authenticate().authorizeTo(TNode(id),Access.WRITE)
    val resp = response
    db.run(NodeMsgsTQ.getMsg(id,msgId).delete.asTry).map({ xs =>
      logger.debug("DELETE /orgs/"+orgid+"/nodes/"+bareId+"/msgs/"+msgId+" result: "+xs.toString)
      xs match {
        case Success(v) => if (v > 0) {        // there were no db errors, but determine if it actually found it or not
            resp.setStatus(HttpCode.DELETED)
            ApiResponse(ApiResponseType.OK, "node msg deleted")
          } else {
            resp.setStatus(HttpCode.NOT_FOUND)
            ApiResponse(ApiResponseType.NOT_FOUND, "msg '"+msgId+"' for node '"+id+"' not found")
          }
        case Failure(t) => resp.setStatus(HttpCode.INTERNAL_ERROR)
          ApiResponse(ApiResponseType.INTERNAL_ERROR, "msg '"+msgId+"' for node '"+id+"' not deleted: "+t.toString)
        }
    })
  })

}
