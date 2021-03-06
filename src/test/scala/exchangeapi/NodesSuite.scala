package exchangeapi

import java.time._
import java.util.Base64

import com.horizon.exchangeapi._
import com.horizon.exchangeapi.tables._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.write
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import scala.collection.immutable._
import scalaj.http._

/**
 * This class is a test suite for the methods in object FunSets. To run
 * the test suite, you can either:
 *  - run the "test" command in the SBT console
 *  - right-click the file in eclipse and chose "Run As" - "JUnit Test"
 *
 * clear and detailed tutorial of FunSuite: http://doc.scalatest.org/1.9.1/index.html#org.scalatest.FunSuite
 */
@RunWith(classOf[JUnitRunner])
class NodesSuite extends FunSuite {

  val localUrlRoot = "http://localhost:8080"
  val urlRoot = sys.env.getOrElse("EXCHANGE_URL_ROOT", localUrlRoot)
  val runningLocally = (urlRoot == localUrlRoot)
  val ACCEPT = ("Accept","application/json")
  val CONTENT = ("Content-Type","application/json")
  val orgid = "NodesSuiteTests"
  val authpref=orgid+"/"
  val URL = urlRoot+"/v1/orgs/"+orgid
  val orgid2 = "NodesSuiteTests2"
  val authpref2=orgid2+"/"
  val URL2 = urlRoot+"/v1/orgs/"+orgid2
  val orgnotthere = orgid+"NotThere"
  val NOORGURL = urlRoot+"/v1"
  val SDRSPEC_URL = "bluehorizon.network.sdr"
  val SDRSPEC = orgid+"/"+SDRSPEC_URL
  val NETSPEEDSPEC_URL = "bluehorizon.network.netspeed"
  val NETSPEEDSPEC = orgid+"/"+NETSPEEDSPEC_URL
  val PWSSPEC_URL = "bluehorizon.network.pws"
  val PWSSPEC = orgid+"/"+PWSSPEC_URL
  val NOTTHERESPEC_URL = "bluehorizon.network.notthere"
  val NOTTHERESPEC = orgid+"/"+NOTTHERESPEC_URL
  val user = "u1"
  val orguser = authpref+user
  val org2user = authpref2+user
  val pw = user+"pw"
  val USERAUTH = ("Authorization","Basic "+orguser+":"+pw)
  val USERAUTH2 = ("Authorization","Basic "+org2user+":"+pw)
  val BADAUTH = ("Authorization","Basic "+orguser+":"+pw+"x")
  val rootuser = Role.superUser
  val rootpw = sys.env.getOrElse("EXCHANGE_ROOTPW", "")      // need to put this root pw in config.json
  val ROOTAUTH = ("Authorization","Basic "+rootuser+":"+rootpw)
  val nodeId = "n1"     // the 1st node created, that i will use to run some rest methods
  val orgnodeId = authpref+nodeId
  val org2nodeId = authpref2+nodeId
  val nodeToken = "mytok"
  val NODEAUTH = ("Authorization","Basic "+orgnodeId+":"+nodeToken)
  val nodeId2 = "n2"
  val orgnodeId2 = authpref+nodeId2
  val nodeToken2 = "my tok"   // intentionally adding a space in the token
  val NODE2AUTH = ("Authorization","Basic "+orgnodeId2+":"+nodeToken2)
  val nodeId3 = "n3"
  val orgnodeId3 = authpref+nodeId3
  val nodeId4 = "n4"
  val orgnodeId4 = authpref+nodeId4
  val nodeId5 = "n5"      // not ever successfully created
  val orgnodeId5 = authpref+nodeId5
  val patid = "p1"
  val compositePatid = orgid+"/"+patid
  val svcid = "bluehorizon.network-services-sdr_1.0.0_amd64"
  //val svcurl = SDRSPEC
  val svcarch = "amd64"
  val svcversion = "1.0.0"
  val svcid2 = "bluehorizon.network-services-netspeed_1.0.0_amd64"
  //val svcurl2 = NETSPEEDSPEC
  val svcarch2 = "amd64"
  val svcversion2 = "1.0.0"
  val agreementId = "agr1"
  val agreementId2 = "agr2"   // for the node in the 2nd org
  val creds = authpref+nodeId+":"+nodeToken
  val encodedCreds = Base64.getEncoder.encodeToString(creds.getBytes("utf-8"))
  val ENCODEDAUTH = ("Authorization","Basic "+encodedCreds)
  val agbotId = "a1"      // need to use a different id than AgbotsSuite.scala, because all of the suites run concurrently
  val orgagbotId = authpref+agbotId
  val agbotToken = agbotId+"tok"
  val AGBOTAUTH = ("Authorization","Basic "+orgagbotId+":"+agbotToken)
  val agbotId2 = "a2"      // need to use a different id than AgbotsSuite.scala, because all of the suites run concurrently
  val orgagbotId2 = authpref+agbotId2
  val agbotToken2 = agbotId2+"tok"
  val AGBOT2AUTH = ("Authorization","Basic "+orgagbotId2+":"+agbotToken2)
  val agProto = "ExchangeAutomatedTest"    // using this to avoid db entries from real users and predefined ones

  implicit val formats = DefaultFormats // Brings in default date formats etc.

  // Operators: test, ignore, pending

  /** Delete all the test orgs */
  def deleteAllOrgs() = {
    for (u <- List(URL, URL2)) {
      val response = Http(u).method("delete").headers(ACCEPT).headers(ROOTAUTH).asString
      info("DELETE "+u+", code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.DELETED || response.code === HttpCode.NOT_FOUND)
    }
  }

  /*
  def deleteAllUsers() = {
    for (i <- List(user)) {
      val response = Http(URL+"/users/"+i).method("delete").headers(ACCEPT).headers(ROOTAUTH).asString
      info("DELETE "+i+", code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.DELETED || response.code === HttpCode.NOT_FOUND)
    }
  }

  def deleteAllNodes() = {
    for (i <- List(nodeId,nodeId2,nodeId3,nodeId4)) {
      val response = Http(URL+"/nodes/"+i).method("delete").headers(ACCEPT).headers(USERAUTH).asString
      info("DELETE "+i+", code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.DELETED || response.code === HttpCode.NOT_FOUND)
    }
  }

  def deleteAllAgreements() = {
    for (i <- List(agreementId)) {
      val response = Http(URL+"/nodes/"+nodeId+"/agreements/"+i).method("delete").headers(ACCEPT).headers(USERAUTH).asString
      info("DELETE "+i+", code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.DELETED || response.code === HttpCode.NOT_FOUND)
    }
  }

  def deleteAllAgbots() = {
    for (i <- List(agbotId)) {
      val response = Http(URL+"/agbots/"+i).method("delete").headers(ACCEPT).headers(USERAUTH).asString
      info("DELETE "+i+", code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.DELETED || response.code === HttpCode.NOT_FOUND)
    }
  }
  */

  /** Patches all of the nodes to have a pattern or blank out the pattern (for node and node health searches) */
  def patchNodePattern(pattern: String): Unit = {
    val jsonInput = """{ "pattern": """"+pattern+"""" }"""
    for (i <- List(nodeId,nodeId2,nodeId3,nodeId4)) {
      val response = Http(URL + "/nodes/" + i).postData(jsonInput).method("patch").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
      info("PATCH "+i+", code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)
    }
  }

  //~~~~~ Create org, user, service, pattern ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  // Delete all the test orgs (and everything under them), in case they exist from a previous run.
  test("Begin - DELETE all test orgs") {
    if (rootpw == "") fail("The exchange root password must be set in EXCHANGE_ROOTPW and must also be put in config.json.")
    deleteAllOrgs()
  }

  test("POST /orgs/"+orgid+" - create org to use for this test suite") {
    val input = PostPutOrgRequest(None, "My Org", "desc", None)
    val response = Http(URL).postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
  }

  test("POST /orgs/"+orgid2+" - create 2nd org to use for this test suite") {
    val input = PostPutOrgRequest(None, "My 2nd Org", "desc", None)
    val response = Http(URL2).postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
  }

  test("POST /orgs/"+orgid+"/users/"+user+" - normal") {
    val input = PostPutUsersRequest(pw, admin = false, user+"@hotmail.com")
    val response = Http(URL+"/users/"+user).postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
  }

  test("POST /orgs/"+orgid2+"/users/"+user+" - normal") {
    val input = PostPutUsersRequest(pw, admin = false, user+"@hotmail.com")
    val response = Http(URL2+"/users/"+user).postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+" - before pattern exists - should fail") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId+"-norm", compositePatid,
      None,
      None, Some(Map("horizon"->"3.2.3")), "NODEABC")
    val response = Http(URL+"/nodes/"+nodeId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/services - add "+svcid+" so pattern can reference it") {
    val input = PostPutServiceRequest("test-service", None, public = false, None, SDRSPEC_URL, svcversion, svcarch, "multiple", None, None, None, None, "", "", None)
    val response = Http(URL+"/services").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
  }

  test("POST /orgs/"+orgid+"/services - add "+svcid2+" so pattern can reference it") {
    val input = PostPutServiceRequest("test-service", None, public = false, None, NETSPEEDSPEC_URL, svcversion2, svcarch2, "multiple", None, None, None, None, "", "", None)
    val response = Http(URL+"/services").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
  }
  // Note: when we delete the org, this service will get deleted

  test("POST /orgs/"+orgid+"/patterns/"+patid+" - so nodes can reference it") {
    val input = PostPutPatternRequest(patid, None, None,
      List(
        // Reference both services in the pattern so we can search on both later on
        PServices(SDRSPEC_URL, orgid, svcarch, None, List(PServiceVersions(svcversion, None, None, None, None)), None, None ),
        PServices(NETSPEEDSPEC_URL, orgid, svcarch2, Some(true), List(PServiceVersions(svcversion2, None, None, None, None)), None, None )
      ),
      None
    )
    val response = Http(URL+"/patterns/"+patid).postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
  }
  // Note: when we delete the org, this pattern will get deleted

  //~~~~~ Create nodes ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  ExchConfig.load()
  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+" - add normal node as user") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId+"-norm", compositePatid,
      Some(List(
        RegService(PWSSPEC,1,Some("active"),"{json policy for "+nodeId+" pws}",List(
          Prop("arch","arm","string","in"),
          Prop("version","1.0.0","version","in"),
          Prop("agreementProtocols",agProto,"list","in"),
          Prop("dataVerification","true","boolean","="))),
        RegService(NETSPEEDSPEC,1,Some("active"),"{json policy for "+nodeId+" netspeed}",List(
          Prop("arch","arm","string","in"),
          Prop("cpus","2","int",">="),
          Prop("version","1.0.0","version","in")))
      )),
      None, Some(Map("horizon"->"3.2.3")), "NODEABC")
    val response = Http(URL+"/nodes/"+nodeId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid2+"/nodes/"+nodeId+" - add node in 2nd org") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId+"-norm", compositePatid, None, None, None, "NODEABCORG2")
    val response = Http(URL2+"/nodes/"+nodeId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH2).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+" - normal - update as user") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId+"-normal-user", compositePatid,
      Some(List(
        RegService(PWSSPEC,1,Some("active"),"{json policy for "+nodeId+" pws}",List(
          Prop("arch","arm","string","in"),
          Prop("version","1.0.0","version","in"),
          Prop("agreementProtocols",agProto,"list","in"),
          Prop("dataVerification","true","boolean","="))),
        RegService(NETSPEEDSPEC,1,Some("active"),"{json policy for "+nodeId+" netspeed}",List(
          Prop("arch","arm","string","in"),
          Prop("cpus","2","int",">="),
          Prop("version","1.0.0","version","in")))
      )),
      None, Some(Map("horizon"->"3.2.3")), "OLDNODEABC")
    val response = Http(URL+"/nodes/"+nodeId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  // this is the last update of nodeId before the GET checks
  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+" - normal update - as node") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId+"-normal", compositePatid,
      Some(List(
        RegService(SDRSPEC,1,Some("active"),"{json policy for "+nodeId+" sdr}",List(
          Prop("arch","arm","string","in"),
          Prop("memory","300","int",">="),
          Prop("version","1.0.0","version","in"),
          Prop("agreementProtocols",agProto,"list","in"),
          Prop("dataVerification","true","boolean","="))),
        RegService(NETSPEEDSPEC,1,None,"{json policy for "+nodeId+" netspeed}",List(  // intentionally setting configState to None to make sure GET displays the default
          Prop("arch","arm","string","in"),
          Prop("agreementProtocols",agProto,"list","in"),
          Prop("version","1.0.0","version","in")))
      )),
      Some(""), Some(Map("horizon"->"3.2.1")), "NODEABC")
    val response = Http(URL+"/nodes/"+nodeId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId2+" - node with higher memory 400, and version 2.0.0") {
    val input = PutNodesRequest(nodeToken2, "rpi"+nodeId2+"-mem-400-vers-2", compositePatid, Some(List(RegService(SDRSPEC,1,Some("active"),"{json policy for "+nodeId2+" sdr}",List(
      Prop("arch","arm","string","in"),
      Prop("memory","400","int",">="),
      Prop("version","2.0.0","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","true","boolean","="))))), None, None, "NODE2ABC")
    val response = Http(URL+"/nodes/"+nodeId2).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId3+" - netspeed-amd64, but no publicKey at 1st") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId3+"-netspeed-amd64", compositePatid, Some(List(RegService(NETSPEEDSPEC,1,Some("active"),"{json policy for "+nodeId3+" netspeed}",List(
      Prop("arch","amd64","string","in"),
      Prop("memory","300","int",">="),
      Prop("version","1.0.0","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","true","boolean","="))))), None, None, "")
    val response = Http(URL+"/nodes/"+nodeId3).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId4+" - bad integer property") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId4+"-bad-int", compositePatid, Some(List(RegService(SDRSPEC,1,Some("active"),"{json policy for "+nodeId4+" sdr}",List(
      Prop("arch","arm","string","in"),
      Prop("memory","400MB","int",">="),
      Prop("version","2.0.0","version","in"),
      Prop("dataVerification","true","boolean","="))))), None, None, "NODE4ABC")
    val response = Http(URL+"/nodes/"+nodeId4).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val putDevResp = parse(response.body).extract[ApiResponse]
    assert(putDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId4+" - bad body format") {
    val badJsonInput = """{
      "token": "foo",
      "xname": "rpi-bad-format",
      "xregisteredServices": [
        {
          "url": """"+SDRSPEC+"""",
          "numAgreements": 1,
          "policy": "{json policy for sdr}",
          "properties": [
            {
              "name": "arch",
              "value": "arm",
              "propType": "string",
              "op": "in"
            }
          ]
        }
      ],
      "softwareVersions": {}
    }"""
    val response = Http(URL+"/nodes/"+nodeId4).postData(badJsonInput).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.BAD_INPUT)     // for now this is what is returned when the json-to-scala conversion fails
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId4+" - bad svc url, but this is currently allowed") {
    val input = PutNodesRequest(nodeToken, "rpi"+nodeId4+"-bad-url", compositePatid, Some(List(RegService(NOTTHERESPEC,1,Some("active"),"{json policy for "+nodeId4+" sdr}",List(
      Prop("arch","arm","string","in"),
      Prop("memory","400","int",">="),
      Prop("version","2.0.0","version","in"),
      Prop("dataVerification","true","boolean","="))))), None, None, "NODE4ABC")
    val response = Http(URL+"/nodes/"+nodeId4).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid+"/agbots/"+agbotId+" - add an agbot so we can test it viewing nodes") {
    val input = PutAgbotsRequest(agbotToken, agbotId+"name", None, "AGBOTABC")
    val response = Http(URL+"/agbots/"+agbotId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  //~~~~~ Get nodes (and some post configState) ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("GET /orgs/"+orgid+"/nodes") {
    // val response: HttpResponse[String] = Http(URL+"/v1/nodes").headers(("Accept","application/json")).param("id","a").param("token","a").asString
    val response: HttpResponse[String] = Http(URL+"/nodes").headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    //info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 4)

    assert(getDevResp.nodes.contains(orgnodeId))
    var dev = getDevResp.nodes(orgnodeId)
    assert(dev.name === "rpi"+nodeId+"-normal")
    assert(dev.registeredServices.length === 2)
    // sdr reg svc
    var svc: RegService = dev.registeredServices.find(m => m.url == SDRSPEC).orNull
    assert(svc !== null)
    assert(svc.url === SDRSPEC)
    assert(svc.configState === Some("active"))
    assert(svc.policy === "{json policy for "+nodeId+" sdr}")
    var archProp = svc.properties.find(p => p.name=="arch").orNull
    assert((archProp !== null) && (archProp.name === "arch"))
    assert(archProp.value === "arm")
    var memProp = svc.properties.find(p => p.name=="memory").orNull
    assert((memProp !== null) && (memProp.value === "300"))
    assert(dev.softwareVersions.size === 1)
    assert(dev.softwareVersions.contains("horizon"))
    assert(dev.softwareVersions("horizon") === "3.2.1")
    // netspeed reg svc
    svc = dev.registeredServices.find(m => m.url==NETSPEEDSPEC).orNull
    assert(svc !== null)
    assert(svc.configState === Some("active"))
    assert(svc.properties.find(p => p.name=="cpus") === None)
    assert(svc.properties.find(p => p.name=="agreementProtocols") !== None)
    assert(dev.registeredServices.find(m => m.url==PWSSPEC) === None)

    assert(getDevResp.nodes.contains(orgnodeId2))
    dev = getDevResp.nodes(orgnodeId2)
    assert(dev.name === "rpi"+nodeId2+"-mem-400-vers-2")
    assert(dev.registeredServices.length === 1)
    svc = dev.registeredServices.head
    assert(svc.url === SDRSPEC)
    assert(svc.policy === "{json policy for "+nodeId2+" sdr}")
    memProp = svc.properties.find(p => p.name=="memory").get
    assert(memProp.value === "400")
    memProp = svc.properties.find(p => p.name=="version").get
    assert(memProp.value === "2.0.0")
    assert(dev.softwareVersions.size === 0)

    assert(getDevResp.nodes.contains(orgnodeId3))
    dev = getDevResp.nodes(orgnodeId3)
    assert(dev.name === "rpi"+nodeId3+"-netspeed-amd64")
    assert(dev.registeredServices.length === 1)
    svc = dev.registeredServices.head
    assert(svc.url === NETSPEEDSPEC)
    archProp = svc.properties.find(p => p.name=="arch").get
    assert(archProp.value === "amd64")
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/services_configstate - invalid config state - should fail") {
    val input = PostNodeConfigStateRequest(orgid, SDRSPEC_URL, "foo")
    val response = Http(URL+"/nodes/"+nodeId+"/services_configstate").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/services_configstate - nonexistant url - should return not found") {
    val input = PostNodeConfigStateRequest(orgid, NOTTHERESPEC_URL, "suspended")
    val response = Http(URL+"/nodes/"+nodeId+"/services_configstate").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.NOT_FOUND)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/services_configstate - nonexistant org - should return not found") {
    val input = PostNodeConfigStateRequest(orgnotthere, SDRSPEC_URL, "suspended")
    val response = Http(URL+"/nodes/"+nodeId+"/services_configstate").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.NOT_FOUND)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - verify none of the bad POSTs above changed the node") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.contains(orgnodeId))
    val dev = getDevResp.nodes(orgnodeId)
    assert(dev.registeredServices.exists(m => m.url == SDRSPEC && m.configState.contains("active")))
    assert(dev.registeredServices.exists(m => m.url == NETSPEEDSPEC && m.configState.contains("active")))
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/services_configstate - change config state of sdr reg svc") {
    val input = PostNodeConfigStateRequest(orgid, SDRSPEC_URL, "suspended")
    val response = Http(URL+"/nodes/"+nodeId+"/services_configstate").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - verify sdr reg svc was suspended") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.contains(orgnodeId))
    val dev = getDevResp.nodes(orgnodeId)
    assert(dev.registeredServices.exists(m => m.url == SDRSPEC && m.configState.contains("suspended")))
    assert(dev.registeredServices.exists(m => m.url == NETSPEEDSPEC && m.configState.contains("active")))
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/services_configstate - change config state of netspeed reg svc") {
    val input = PostNodeConfigStateRequest("", NETSPEEDSPEC_URL, "suspended")
    val response = Http(URL+"/nodes/"+nodeId+"/services_configstate").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - verify netspeed reg svc was suspended") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.contains(orgnodeId))
    val dev = getDevResp.nodes(orgnodeId)
    assert(dev.registeredServices.exists(m => m.url == SDRSPEC && m.configState.contains("suspended")))
    assert(dev.registeredServices.exists(m => m.url == NETSPEEDSPEC && m.configState.contains("suspended")))
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/services_configstate - change config state of all reg svcs back to active") {
    val input = PostNodeConfigStateRequest("", "", "active")
    val response = Http(URL+"/nodes/"+nodeId+"/services_configstate").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - verify all reg svcs back to active") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.contains(orgnodeId))
    val dev = getDevResp.nodes(orgnodeId)
    assert(dev.registeredServices.exists(m => m.url == SDRSPEC && m.configState.contains("active")))
    assert(dev.registeredServices.exists(m => m.url == NETSPEEDSPEC && m.configState.contains("active")))
  }

  test("GET /orgs/"+orgid+"/nodes - filter owner and name") {
    val response: HttpResponse[String] = Http(URL+"/nodes").headers(ACCEPT).headers(USERAUTH).param("owner",orgid+"/"+user).param("name","rpi%netspeed%amd64").asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 1)
    assert(getDevResp.nodes.contains(orgnodeId3))
  }

  test("GET /orgs/"+orgid+"/nodes - filter owner and idfilter") {
    val response: HttpResponse[String] = Http(URL+"/nodes").headers(ACCEPT).headers(USERAUTH).param("owner",orgid+"/"+user).param("idfilter",orgid+"/n%").asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 4)
    assert(getDevResp.nodes.contains(orgnodeId))
    assert(getDevResp.nodes.contains(orgnodeId2))
    assert(getDevResp.nodes.contains(orgnodeId3))
    assert(getDevResp.nodes.contains(orgnodeId4))
  }

  test("GET /orgs/"+orgid+"/nodes - bad creds") {
    // val response: HttpResponse[String] = Http(URL+"/v1/nodes").headers(("Accept","application/json")).param("id","a").param("token","a").asString
    val response: HttpResponse[String] = Http(URL+"/nodes").headers(ACCEPT).headers(BADAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BADCREDS)
  }

  test("GET /orgs/"+orgid+"/nodes - by agbot") {
    val response: HttpResponse[String] = Http(URL+"/nodes").headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 4)
  }

  test("GET /orgs/"+orgid+" - "+nodeId+" should be able to read his own org") {
    val response: HttpResponse[String] = Http(URL).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/heartbeat") {
    val response = Http(URL+"/nodes/"+nodeId+"/heartbeat").method("post").headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val devResp = parse(response.body).extract[ApiResponse]
    assert(devResp.code === ApiResponseType.OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId) {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 1)

    assert(getDevResp.nodes.contains(orgnodeId))
    val dev = getDevResp.nodes(orgnodeId)
    assert(dev.name === "rpi"+nodeId+"-normal")

    // Verify the lastHeartbeat from the POST heartbeat above is within a few seconds of now. Format is: 2016-09-29T13:04:56.850Z[UTC]
    val now: Long = System.currentTimeMillis / 1000     // seconds since 1/1/1970
    val lastHb = ZonedDateTime.parse(dev.lastHeartbeat).toEpochSecond
    assert(now - lastHb <= 3)    // should not now be more than 3 seconds from the time the heartbeat was done above

    assert(dev.registeredServices.length === 2)
    val svc: RegService = dev.registeredServices.find(m => m.url==SDRSPEC).orNull
    assert(svc !== null)
    assert(svc.url === SDRSPEC)
    assert(svc.policy === "{json policy for "+nodeId+" sdr}")
    var archProp = svc.properties.find(p => p.name=="arch").orNull
    assert((archProp !== null) && (archProp.name === "arch"))
    assert(archProp.value === "arm")
    var memProp = svc.properties.find(p => p.name=="memory").orNull
    assert((memProp !== null) && (memProp.value === "300"))

    assert(dev.registeredServices.find(m => m.url==NETSPEEDSPEC) !== None)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - as node") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 1)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - as node - encoded") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(ENCODEDAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 1)
    assert(getDevResp.nodes.contains(orgnodeId))
    val dev = getDevResp.nodes(orgnodeId)
    assert(dev.name === "rpi"+nodeId+"-normal")
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - as agbot") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 1)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - as node, with token in URL parms, but no id") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId+"?token="+nodeToken).headers(ACCEPT).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 1)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - as user in the URL params") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId+"?id="+user+"&token="+pw).headers(ACCEPT).asString
    info("code: "+response.code)
    //info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.size === 1)
  }

  test("PATCH /orgs/"+orgid+"/nodes/"+nodeId+" - as node") {
    val jsonInput = """{ "publicKey": "NODEABC" }"""
    val response = Http(URL+"/nodes/"+nodeId).postData(jsonInput).method("patch").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+" - as node, check patch by getting that 1 attr") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId+"?attribute=publicKey").headers(ACCEPT).headers(NODEAUTH).asString
    //info("code: "+response.code)
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getNodeResp = parse(response.body).extract[GetNodeAttributeResponse]
    assert(getNodeResp.attribute === "publicKey")
    assert(getNodeResp.value === "NODEABC")
}

  test("GET /orgs/"+orgid+"/nodes/"+nodeId4) {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId4).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
  }

  //~~~~~ Pattern search and nodehealth ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+SDRSPEC+" - as agbot - should not find "+nodeId3+" because no publicKey") {
    val input = PostPatternSearchRequest(SDRSPEC, Some(List(orgid,orgid2)), 86400, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 4)
    assert(nodes.count(d => d.id==orgnodeId || d.id==org2nodeId || d.id==orgnodeId2 || d.id==orgnodeId4) === 4)
    val dev = nodes.find(d => d.id == orgnodeId).get // the 2nd get turns the Some(val) into val
    assert(dev.publicKey === "NODEABC")
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+PWSSPEC+" which is not in the pattern, so should fail") {
    val input = PostPatternSearchRequest(PWSSPEC, None, 86400, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    //info("code: "+response.code)
    assert(response.code === HttpCode.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/nodehealth - as agbot, with blank time, and both orgs - should find all nodes") {
    val input = PostNodeHealthRequest("", Some(List(orgid,orgid2)))
    val response = Http(URL+"/patterns/"+patid+"/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 5)
    assert(nodes.contains(orgnodeId) && nodes.contains(orgnodeId2) && nodes.contains(orgnodeId3) && nodes.contains(orgnodeId4))
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/nodehealth - as agbot, with current time - should get no nodes") {
    //Thread.sleep(500)    // delay 0.5 seconds so no agreements will be current
    val currentTime = ApiTime.futureUTC(100000)   // sometimes there is a mismatch between the exch svr time and this client's time
    info("currentTime: "+currentTime)
    val input = PostNodeHealthRequest(currentTime, None)
    val response = Http(URL+"/patterns/"+patid+"/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    //info("code: "+response.code)
    assert(response.code === HttpCode.NOT_FOUND)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 0)
  }

  //~~~~~ Node search and nodehealth ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("PATCH /orgs/"+orgid+"/nodes/"+nodeId3+" - add publicKey") {
    val jsonInput = """{ "publicKey": "NODE3ABC" }"""
    val response = Http(URL + "/nodes/" + nodeId3).postData(jsonInput).method("patch").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    assert(response.code === HttpCode.PUT_OK)
  }

  test("POST /orgs/"+orgid+"/search/nodes - all arm nodes") {
    patchNodePattern("")      // remove pattern from nodes so we can search for services
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","arm","string","in"),
      Prop("memory","2","int",">="),
      Prop("version","*","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 2)     // we created 2 arm nodes
    assert(nodes.count(d => d.id==orgnodeId || d.id==orgnodeId2) === 2)
    val dev = nodes.find(d => d.id == orgnodeId).get // the 2nd get turns the Some(val) into val
    assert(dev.name === "rpi"+nodeId+"-normal")
    assert(dev.services.length === 1)
    val svc = dev.services.head
    assert(svc.url === SDRSPEC)
    assert(svc.policy === "{json policy for "+nodeId+" sdr}")
    var archProp = svc.properties.find(p => p.name=="arch").orNull
    assert((archProp !== null) && (archProp.name === "arch"))
    assert(archProp.value === "arm")
  }

  test("POST /orgs/"+orgid+"/search/nodes - netspeed arch amd64 - as agbot") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(NETSPEEDSPEC,List(
      Prop("arch","amd64","string","in"),
      Prop("memory","*","int",">="),
      Prop("version","[1.0.0,2.0.0]","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 1)
    assert(nodes.count(d => d.id==orgnodeId3) === 1)
  }

  test("POST /orgs/"+orgid+"/search/nodes - netspeed arch * - as agbot") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(NETSPEEDSPEC,List(
      Prop("arch","*","string","in"),
      Prop("memory","*","int",">="),
      Prop("version","[1.0.0,2.0.0]","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 2)
    assert(nodes.count(d => d.id==orgnodeId) === 1)
    assert(nodes.count(d => d.id==orgnodeId3) === 1)
  }

  test("POST /orgs/"+orgid+"/search/nodes - netspeed and sdr - as agbot") {
    val input = PostSearchNodesRequest(List(
      RegServiceSearch(NETSPEEDSPEC,List(
        Prop("arch","*","string","in"),
        Prop("memory","*","int",">="),
        Prop("version","[1.0.0,2.0.0]","version","in"),
        Prop("agreementProtocols",agProto,"list","in"),
        Prop("dataVerification","","wildcard","="))),
      RegServiceSearch(SDRSPEC,List(
        Prop("arch","arm","string","in"),
        Prop("memory","2","int",">="),
        Prop("version","*","version","in"),
        Prop("agreementProtocols",agProto,"list","in"),
        Prop("dataVerification","","wildcard","="))
    )), 86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 1)
    assert(nodes.count(d => d.id==orgnodeId) === 1)  // this confirms it did get nodeId (2 MSs) and did not get nodeId2 (only 1 of the MSs)
    val dev = nodes.find(d => d.id == orgnodeId).get // the 2nd get turns the Some(val) into val
    assert(dev.services.length === 2)
    assert(dev.services.count(m => m.url==SDRSPEC) === 1)
    assert(dev.services.count(m => m.url==NETSPEEDSPEC) === 1)
  }

  test("POST /orgs/"+orgid+"/search/nodes - arch list, mem 400, version 2.0.0") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","arm,amd64","list","in"),
      Prop("memory","400","int",">="),
      Prop("version","2.0.0,3.0.0","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","true","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 1)
    assert(nodes.count(d => d.id==orgnodeId2) === 1)
  }

  test("POST /orgs/"+orgid+"/search/nodes - data verification false - should find no matches") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","","wildcard","in"),
      Prop("memory","","wildcard",">="),
      Prop("version","0","version","in"),     // in osgi version format 0 means lower bound is 0 and upper bound infinity
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","false","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    assert(postSearchDevResp.nodes.length === 0)
  }

  test("POST /orgs/"+orgid+"/search/nodes - invalid propType") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","","stringx","in"),
      Prop("memory","","int",">="),
      Prop("version","","version","in"),
      Prop("dataVerification","","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val postSearchDevResp = parse(response.body).extract[ApiResponse]
    assert(postSearchDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/search/nodes - invalid op") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","","string","inx"),
      Prop("memory","","int",">="),
      Prop("version","","version","in"),
      Prop("dataVerification","","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val postSearchDevResp = parse(response.body).extract[ApiResponse]
    assert(postSearchDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/search/nodes - invalid version") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","*","string","in"),
      Prop("memory","*","int",">="),
      Prop("version","1.2.3.4","version","in"),
      Prop("dataVerification","*","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val postSearchDevResp = parse(response.body).extract[ApiResponse]
    assert(postSearchDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/search/nodes - invalid boolean/op combo") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","","string","in"),
      Prop("memory","","int",">="),
      Prop("version","","version","in"),
      Prop("dataVerification","","boolean","in")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val postSearchDevResp = parse(response.body).extract[ApiResponse]
    assert(postSearchDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/search/nodes - invalid string/op combo") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","","string","="),
      Prop("memory","","int",">="),
      Prop("version","","version","in"),
      Prop("dataVerification","","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val postSearchDevResp = parse(response.body).extract[ApiResponse]
    assert(postSearchDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/search/nodes - invalid int/op combo") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","","string","in"),
      Prop("memory","","int","in"),
      Prop("version","","version","in"),
      Prop("dataVerification","","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val postSearchDevResp = parse(response.body).extract[ApiResponse]
    assert(postSearchDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/search/nodes - invalid version/op combo") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","","string","in"),
      Prop("memory","","int",">="),
      Prop("version","","version",">="),
      Prop("dataVerification","","boolean","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.BAD_INPUT)
    val postSearchDevResp = parse(response.body).extract[ApiResponse]
    assert(postSearchDevResp.code === ApiResponseType.BAD_INPUT)
  }

  test("POST /orgs/"+orgid+"/search/nodehealth - as agbot, with blank time - should find all nodes") {
    val input = PostNodeHealthRequest("", None)
    val response = Http(URL+"/search/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 4)
    assert(nodes.contains(orgnodeId) && nodes.contains(orgnodeId2) && nodes.contains(orgnodeId3) && nodes.contains(orgnodeId4))
  }

  test("POST /orgs/"+orgid+"/search/nodehealth - as agbot, with current time - should get no nodes") {
    //Thread.sleep(500)    // delay 0.5 seconds so no agreements will be current
    val input = PostNodeHealthRequest(ApiTime.futureUTC(100000), None)
    val response = Http(URL+"/search/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.NOT_FOUND)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 0)
  }

  //~~~~~ Node status ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+"/status - as node") {
    val input = PutNodeStatusRequest(Map[String,Boolean]("images.bluehorizon.network" -> true), List[OneService]())
    val response = Http(URL+"/nodes/"+nodeId+"/status").postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+"/status - as node") {
    val response = Http(URL+"/nodes/"+nodeId+"/status").method("get").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getResp = parse(response.body).extract[NodeStatus]
    assert(getResp.connectivity("images.bluehorizon.network") === true)
  }

  test("DELETE /orgs/"+orgid+"/nodes/"+nodeId+"/status - as node") {
    val response = Http(URL+"/nodes/"+nodeId+"/status").method("delete").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.DELETED)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+"/status - as node - should not be there") {
    val response = Http(URL+"/nodes/"+nodeId+"/status").method("get").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.NOT_FOUND)
  }

  //~~~~~ Node agreements, and more searches and nodehealth ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" - create agreement, as node") {
    val input = PutNodeAgreementRequest(Some(List(NAService(orgid,SDRSPEC_URL))), Some(NAgrService(orgid,patid,SDRSPEC)), "signed")
    val response = Http(URL+"/nodes/"+nodeId+"/agreements/"+agreementId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" - update agreement as node") {
    val input = PutNodeAgreementRequest(Some(List(NAService(orgid,SDRSPEC_URL))), Some(NAgrService(orgid,patid,SDRSPEC)), "finalized")
    val response = Http(URL+"/nodes/"+nodeId+"/agreements/"+agreementId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" - update agreement as user") {
    val input = PutNodeAgreementRequest(Some(List(NAService(orgid,SDRSPEC_URL))), Some(NAgrService(orgid,patid,SDRSPEC)), "negotiating")
    val response = Http(URL+"/nodes/"+nodeId+"/agreements/"+agreementId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+SDRSPEC+" - with "+nodeId+" in agreement") {
    patchNodePattern(compositePatid)      // put pattern back in nodes so we can search for pattern nodes
    val input = PostPatternSearchRequest(SDRSPEC, Some(List(orgid,orgid2)), 86400, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    //info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 4)
    assert(nodes.count(d => d.id==org2nodeId || d.id==orgnodeId2 || d.id==orgnodeId3 || d.id==orgnodeId4) === 4)
  }

  test("PUT /orgs/"+orgid2+"/nodes/"+nodeId+"/agreements/"+agreementId2+" - create agreement for node in 2nd org, with short old style url") {
    val input = PutNodeAgreementRequest(Some(List(NAService(orgid,SDRSPEC_URL))), Some(NAgrService(orgid,patid,SDRSPEC_URL)), "signed")
    val response = Http(URL2+"/nodes/"+nodeId+"/agreements/"+agreementId2).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH2).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+SDRSPEC+" - with "+org2nodeId+" in agreement") {
    //patchNodePattern(compositePatid)      // put pattern back in nodes so we can search for pattern nodes
    val input = PostPatternSearchRequest(SDRSPEC, Some(List(orgid,orgid2)), 86400, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    //info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 3)
    assert(nodes.count(d => d.id==orgnodeId2 || d.id==orgnodeId3 || d.id==orgnodeId4) === 3)
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/nodehealth - as agbot, with blank time - should find all nodes in both orgs and 1 agreement for "+nodeId+" in each org") {
    val input = PostNodeHealthRequest("", Some(List(orgid,orgid2)))
    val response = Http(URL+"/patterns/"+patid+"/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 5)
    assert(nodes.contains(orgnodeId) && nodes.contains(org2nodeId) && nodes.contains(orgnodeId2) && nodes.contains(orgnodeId3) && nodes.contains(orgnodeId4))
    var dev = nodes(orgnodeId)
    assert(dev.agreements.contains(agreementId))
    dev = nodes(org2nodeId)
    assert(dev.agreements.contains(agreementId2))
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/nodehealth - as agbot, with blank time - should find all nodes in the 1st orgs and 1 agreement for "+nodeId) {
    val input = PostNodeHealthRequest("", None)
    val response = Http(URL+"/patterns/"+patid+"/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 4)
    assert(nodes.contains(orgnodeId) && nodes.contains(orgnodeId2) && nodes.contains(orgnodeId3) && nodes.contains(orgnodeId4))
    val dev = nodes(orgnodeId)
    assert(dev.agreements.contains(agreementId))
  }

  test("POST /orgs/"+orgid+"/search/nodes - netspeed and sdr - now no nodes, since 1 agreement made") {
    patchNodePattern("")      // remove pattern from nodes so we can search for services
    val input = PostSearchNodesRequest(List(
      RegServiceSearch(NETSPEEDSPEC,List(
        Prop("arch","*","string","in"),
        Prop("memory","*","int",">="),
        Prop("version","[1.0.0,2.0.0]","version","in"),
        Prop("agreementProtocols",agProto,"list","in"),
        Prop("dataVerification","","wildcard","="))),
      RegServiceSearch(SDRSPEC,List(
        Prop("arch","arm","string","in"),
        Prop("memory","2","int",">="),
        Prop("version","*","version","in"),
        Prop("agreementProtocols",agProto,"list","in"),
        Prop("dataVerification","","wildcard","="))
    )), 86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 0)
  }

  test("POST /orgs/"+orgid+"/search/nodehealth - as agbot, with blank time - should find all nodes and 1 agreement for "+nodeId) {
    val input = PostNodeHealthRequest("", None)
    val response = Http(URL+"/search/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 4)
    assert(nodes.contains(orgnodeId) && nodes.contains(orgnodeId2) && nodes.contains(orgnodeId3) && nodes.contains(orgnodeId4))
    val dev = nodes(orgnodeId)
    assert(dev.agreements.contains(agreementId))
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/9951 - add 2nd agreement as node") {
    val input = PutNodeAgreementRequest(Some(List(NAService(orgid,"pws"))), Some(NAgrService(orgid,patid,"pws")), "signed")
    val response = Http(URL+"/nodes/"+nodeId+"/agreements/9951").postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+"/agreements - verify node agreement") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId+"/agreements").headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
    val getAgResp = parse(response.body).extract[GetNodeAgreementsResponse]
    assert(getAgResp.agreements.size === 2)

    assert(getAgResp.agreements.contains(agreementId))
    val ag = getAgResp.agreements(agreementId)
    assert(ag.services === List[NAService](NAService(orgid,SDRSPEC_URL)))
    assert(ag.state === "negotiating")
    assert(getAgResp.agreements.contains("9951"))
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId) {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId+"/agreements/"+agreementId).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
    val getAgResp = parse(response.body).extract[GetNodeAgreementsResponse]
    assert(getAgResp.agreements.size === 1)

    assert(getAgResp.agreements.contains(agreementId))
    val ag = getAgResp.agreements(agreementId)
    assert(ag.services === List[NAService](NAService(orgid,SDRSPEC_URL)))
    assert(ag.state === "negotiating")

    info("GET /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" output verified")
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" - as node") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId+"/agreements/"+agreementId).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.OK)
    val getAgResp = parse(response.body).extract[GetNodeAgreementsResponse]
    assert(getAgResp.agreements.size === 1)

    info("GET /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" as node output verified")
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+SDRSPEC+" - with "+nodeId+" in agreement, should get same result as before") {
    patchNodePattern(compositePatid)      // put pattern back in nodes so we can search for pattern nodes
    val input = PostPatternSearchRequest(SDRSPEC, None, 86400, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 3)
    assert(nodes.count(d => d.id==orgnodeId2 || d.id==orgnodeId3 || d.id==orgnodeId4) === 3)
  }

  test("POST /orgs/"+orgid+"/search/nodes - all arm nodes, should get 1 less result, because "+nodeId+" in agreement") {
    patchNodePattern("")      // remove pattern from nodes so we can search for services
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","arm","string","in"),
      Prop("memory","*","int",">="),
      Prop("version","","wildcard","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 1 || nodes.length === 2)     // UsersSuite may have created 1
    assert(nodes.count(d => d.id==orgnodeId2) === 1)
  }

  test("POST /orgs/"+orgid+"/search/nodes - netspeed arch arm, "+nodeId+" sdr in agreement, but netspeed not, so still should find it - as agbot") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(NETSPEEDSPEC,List(
      Prop("arch","arm","string","in"),
      Prop("memory","*","int",">="),
      Prop("version","[1.0.0,2.0.0]","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 1)
    assert(nodes.count(d => d.id==orgnodeId) === 1)
  }

  test("DELETE /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" - sdr") {
    val response = Http(URL+"/nodes/"+nodeId+"/agreements/"+agreementId).method("delete").headers(ACCEPT).headers(NODEAUTH).asString
    info("DELETE "+agreementId+", code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.DELETED)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/"+agreementId+" - netspeed") {
    val input = PutNodeAgreementRequest(Some(List(NAService(orgid,NETSPEEDSPEC_URL))), Some(NAgrService(orgid,patid,NETSPEEDSPEC)), "signed")
    val response = Http(URL+"/nodes/"+nodeId+"/agreements/"+agreementId).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+NETSPEEDSPEC+" - with "+nodeId+" in agreement") {
    patchNodePattern(compositePatid)      // put pattern back in nodes so we can search for pattern nodes
    val input = PostPatternSearchRequest(NETSPEEDSPEC, None, 86400, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 3)
    assert(nodes.count(d => d.id==orgnodeId2 || d.id==orgnodeId3 || d.id==orgnodeId4) === 3)
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+SDRSPEC+" - should find all nodes again") {
    val input = PostPatternSearchRequest(SDRSPEC, None, 86400, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 4)
    assert(nodes.count(d => d.id==orgnodeId || d.id==orgnodeId2 || d.id==orgnodeId3 || d.id==orgnodeId4) === 4)
    val dev = nodes.find(d => d.id == orgnodeId).get // the 2nd get turns the Some(val) into val
    assert(dev.publicKey === "NODEABC")
  }

  test("POST /orgs/"+orgid+"/search/nodes - netspeed arch arm, "+nodeId+" netspeed in agreement, so shouldn't find it - as agbot") {
    patchNodePattern("")      // remove pattern from nodes so we can search for services
    val input = PostSearchNodesRequest(List(RegServiceSearch(NETSPEEDSPEC,List(
      Prop("arch","arm","string","in"),
      Prop("memory","*","int",">="),
      Prop("version","[1.0.0,2.0.0]","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 0)
  }

  test("POST /orgs/"+orgid+"/search/nodes - sdr arch arm, "+nodeId+" netspeed in agreement, but should still find the sdr - as agbot") {
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","arm","string","in"),
      Prop("memory","*","int",">="),
      Prop("version","[1.0.0,2.0.0]","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      86400, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    // info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 2 || nodes.length === 3)      // UsersSuite creates 1 too
    assert(nodes.count(d => d.id==orgnodeId) === 1)
  }

  //todo: add tests for searching for multiple MS URLs in 1 call

  //~~~~~ Staleness tests ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+SDRSPEC+" - all nodes stale") {
    patchNodePattern(compositePatid)      // put pattern back in nodes so we can search for pattern nodes
    Thread.sleep(1100)    // delay 1.1 seconds so all nodes will be stale
    val input = PostPatternSearchRequest(SDRSPEC, None, 1, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.NOT_FOUND)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 0)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/heartbeat - so this node won't be stale for pattern search") {
    //nodeHealthLastTime = ApiTime.nowUTC     // saving this for the nodehealth call in the next test
    val response = Http(URL+"/nodes/"+nodeId+"/heartbeat").method("post").headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val devResp = parse(response.body).extract[ApiResponse]
    assert(devResp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/nodehealth - as agbot, after heartbeat - should find 1 node and 1 agreement for "+nodeId) {
    // The time sync between exch svr and this client is not reliable, so get the actual last update time of the node we are after
    //Thread.sleep(500)    // delay 0.5 seconds so no agreements will be current
    var response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val getDevResp = parse(response.body).extract[GetNodesResponse]
    assert(getDevResp.nodes.contains(orgnodeId))
    val node = getDevResp.nodes(orgnodeId)
    val nodeHealthLastTime = node.lastHeartbeat

    val input = PostNodeHealthRequest(nodeHealthLastTime, None)
    response = Http(URL+"/patterns/"+patid+"/nodehealth").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postResp = parse(response.body).extract[PostNodeHealthResponse]
    val nodes = postResp.nodes
    assert(nodes.size === 1)
    assert(nodes.contains(orgnodeId))
    val dev = nodes(orgnodeId)
    assert(dev.agreements.contains(agreementId))
  }

  test("POST /orgs/"+orgid+"/patterns/"+patid+"/search - for "+SDRSPEC+" - 1 node not stale") {
    val input = PostPatternSearchRequest(SDRSPEC, None, 1, 0, 0)
    val response = Http(URL+"/patterns/"+patid+"/search").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    //info("code: "+response.code+", response.body: "+response.body)
    info("code: "+response.code)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostPatternSearchResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 1)
    assert(nodes.count(d => d.id==orgnodeId) === 1)
  }

  test("POST /orgs/"+orgid+"/search/nodes - all arm nodes, but all stale") {
    patchNodePattern("")      // remove pattern from nodes so we can search for services
    Thread.sleep(1100)    // delay 1.1 seconds so all nodes will be stale
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","arm","string","in"),
      Prop("memory","2","int",">="),
      Prop("version","*","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      1, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.NOT_FOUND)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 0)
}

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/heartbeat - so this node won't be stale for non-pattern search") {
    val response = Http(URL+"/nodes/"+nodeId+"/heartbeat").method("post").headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val devResp = parse(response.body).extract[ApiResponse]
    assert(devResp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/search/nodes - all arm nodes, 1 not stale") {
    val secondsNotStale = 1
    info("secondsNotStale: "+secondsNotStale)
    val input = PostSearchNodesRequest(List(RegServiceSearch(SDRSPEC,List(
      Prop("arch","arm","string","in"),
      Prop("memory","2","int",">="),
      Prop("version","*","version","in"),
      Prop("agreementProtocols",agProto,"list","in"),
      Prop("dataVerification","","wildcard","=")))),
      secondsNotStale, None, 0, 0)
    val response = Http(URL+"/search/nodes").postData(write(input)).headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    //info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val postSearchDevResp = parse(response.body).extract[PostSearchNodesResponse]
    val nodes = postSearchDevResp.nodes
    assert(nodes.length === 1)
    assert(nodes.count(d => d.id==orgnodeId) === 1)
  }

  test("DELETE /orgs/"+orgid+"/nodes/"+nodeId3+" - explicit delete of "+nodeId3) {
    var response = Http(URL+"/nodes/"+nodeId3).method("delete").headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.DELETED)

    response = Http(URL+"/nodes/"+nodeId3).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    // info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.NOT_FOUND)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId+"/agreements/9952 - Try to add a 3rd agreement with low maxAgreements") {
    if (runningLocally) {     // changing limits via POST /admin/config does not work in multi-node mode
      // Get the current config value so we can restore it afterward
      // ExchConfig.load  <-- already do this earlier
      val origMaxAgreements = ExchConfig.getInt("api.limits.maxAgreements")

      // Change the maxAgreements config value in the svr
      var configInput = AdminConfigRequest("api.limits.maxAgreements", "1")
      var response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)

      // Now try adding another agreement - expect it to be rejected
      val input = PutNodeAgreementRequest(Some(List(NAService(orgid,"netspeed"))), Some(NAgrService(orgid,patid,"netspeed")), "signed")
      response = Http(URL+"/nodes/"+nodeId+"/agreements/9952").postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.ACCESS_DENIED)
      val respObj = parse(response.body).extract[ApiResponse]
      assert(respObj.msg.contains("Access Denied"))

      // Restore the maxAgreements config value in the svr
      configInput = AdminConfigRequest("api.limits.maxAgreements", origMaxAgreements.toString)
      response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)
    }
  }

  test("DELETE /orgs/"+orgid+"/nodes/"+nodeId+"/agreements - all agreements") {
    val response = Http(URL+"/nodes/"+nodeId+"/agreements").method("delete").headers(ACCEPT).headers(USERAUTH).asString
    info("DELETE agreements, code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.DELETED)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+"/agreements - verify all agreements gone") {
    val response: HttpResponse[String] = Http(URL+"/nodes/"+nodeId+"/agreements").headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.NOT_FOUND)
    val getAgResp = parse(response.body).extract[GetNodeAgreementsResponse]
    assert(getAgResp.agreements.size === 0)
  }

  test("PUT /orgs/"+orgid+"/nodes/"+nodeId5+" - with low maxNodes") {
    if (runningLocally) {     // changing limits via POST /admin/config does not work in multi-node mode
      // Get the current config value so we can restore it afterward
      // ExchConfig.load  <-- already do this earlier
      val origMaxNodes = ExchConfig.getInt("api.limits.maxNodes")

      // Change the maxNodes config value in the svr
      var configInput = AdminConfigRequest("api.limits.maxNodes", "2")
      var response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)

      // Now try adding another node - expect it to be rejected
      val input = PutNodesRequest(nodeToken, "rpi"+nodeId5+"-netspeed", compositePatid, Some(List(RegService(NETSPEEDSPEC,1,Some("active"),"{json policy for "+nodeId5+" netspeed}",List(
        Prop("arch","arm","string","in"),
        Prop("version","1.0.0","version","in"),
        Prop("agreementProtocols",agProto,"list","in"))))), None, None, "NODE4ABC")
      response = Http(URL+"/nodes/"+nodeId5).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.ACCESS_DENIED)
      val respObj = parse(response.body).extract[ApiResponse]
      assert(respObj.msg.contains("Access Denied"))

      // Restore the maxNodes config value in the svr
      configInput = AdminConfigRequest("api.limits.maxNodes", origMaxNodes.toString)
      response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)
    }
  }

  //~~~~~ Node messages ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("PUT /orgs/"+orgid+"/agbots/"+agbotId2+" - add a 2nd agbot so we can test msgs") {
    val input = PutAgbotsRequest(agbotToken2, agbotId2+"name", None, "AGBOT2ABC")
    val response = Http(URL+"/agbots/"+agbotId2).postData(write(input)).method("put").headers(CONTENT).headers(ACCEPT).headers(USERAUTH).asString
    info("code: "+response.code)
    assert(response.code === HttpCode.PUT_OK)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/msgs - Send a msg from agbot1 to node1") {
    val input = PostNodesMsgsRequest("{msg1 from agbot1 to node1}", 300)
    val response = Http(URL+"/nodes/"+nodeId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/msgs - short ttl so it will expire") {
    val input = PostNodesMsgsRequest("{msg1 from agbot1 to node1 with 1 second ttl}", 1)
    val response = Http(URL+"/nodes/"+nodeId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/msgs - 2nd msg from agbot1 to node1") {
    val input = PostNodesMsgsRequest("{msg2 from agbot1 to node1}", 300)
    val response = Http(URL+"/nodes/"+nodeId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/msgs - from agbot2 to node1") {
    val input = PostNodesMsgsRequest("{msg1 from agbot2 to node1}", 300)
    val response = Http(URL+"/nodes/"+nodeId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(AGBOT2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId2+"/msgs - from agbot2 to node2") {
    val input = PostNodesMsgsRequest("{msg1 from agbot2 to node2}", 300)
    val response = Http(URL+"/nodes/"+nodeId2+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(AGBOT2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId+"/msgs") {
    Thread.sleep(1100)    // delay 1.1 seconds so 1 of the msgs will expire
    val response = Http(URL+"/nodes/"+nodeId+"/msgs").method("get").headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val resp = parse(response.body).extract[GetNodeMsgsResponse]
    assert(resp.messages.size === 3)
    var msg = resp.messages.find(m => m.message=="{msg1 from agbot1 to node1}").orNull
    assert(msg !== null)
    assert(msg.agbotId === orgagbotId)
    assert(msg.agbotPubKey === "AGBOTABC")

    msg = resp.messages.find(m => m.message=="{msg2 from agbot1 to node1}").orNull
    assert(msg !== null)
    assert(msg.agbotId === orgagbotId)
    assert(msg.agbotPubKey === "AGBOTABC")

    msg = resp.messages.find(m => m.message=="{msg1 from agbot2 to node1}").orNull
    assert(msg !== null)
    assert(msg.agbotId === orgagbotId2)
    assert(msg.agbotPubKey === "AGBOT2ABC")
  }

  test("GET /orgs/"+orgid+"/nodes/"+nodeId2+"/msgs - then delete and get again") {
    var response = Http(URL+"/nodes/"+nodeId2+"/msgs").method("get").headers(ACCEPT).headers(NODE2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val resp = parse(response.body).extract[GetNodeMsgsResponse]
    assert(resp.messages.size === 1)
    val msg = resp.messages.find(m => m.message == "{msg1 from agbot2 to node2}").orNull
    assert(msg !== null)
    assert(msg.agbotId === orgagbotId2)
    assert(msg.agbotPubKey === "AGBOT2ABC")
    val msgId = msg.msgId

    response = Http(URL+"/nodes/"+nodeId2+"/msgs/"+msgId).method("delete").headers(ACCEPT).headers(NODE2AUTH).asString
    info("DELETE "+msgId+", code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.DELETED)

    response = Http(URL+"/nodes/"+nodeId2+"/msgs").method("get").headers(ACCEPT).headers(NODE2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.NOT_FOUND)
    val resp2 = parse(response.body).extract[GetNodeMsgsResponse]
    assert(resp2.messages.size === 0)
  }


  test("POST /orgs/"+orgid+"/agbots/"+agbotId+"/msgs from node1 to agbot1") {
    val input = PostAgbotsMsgsRequest("{msg1 from node1 to agbot1}", 300)
    val response = Http(URL+"/agbots/"+agbotId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/agbots/"+agbotId+"/msgs - short ttl so it will expire") {
    val input = PostAgbotsMsgsRequest("{msg1 from node1 to agbot1 with 1 second ttl}", 1)
    val response = Http(URL+"/agbots/"+agbotId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/agbots/"+agbotId+"/msgs - 2nd msg from node1 to agbot1") {
    val input = PostAgbotsMsgsRequest("{msg2 from node1 to agbot1}", 300)
    val response = Http(URL+"/agbots/"+agbotId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/agbots/"+agbotId+"/msgs - from node2 to agbot1") {
    val input = PostAgbotsMsgsRequest("{msg1 from node2 to agbot1}", 300)
    val response = Http(URL+"/agbots/"+agbotId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(NODE2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("POST /orgs/"+orgid+"/agbots/"+agbotId2+"/msgs - from node2 to agbot2") {
    val input = PostAgbotsMsgsRequest("{msg1 from node2 to agbot2}", 300)
    val response = Http(URL+"/agbots/"+agbotId2+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(NODE2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.POST_OK)
    val resp = parse(response.body).extract[ApiResponse]
    assert(resp.code === ApiResponseType.OK)
  }

  test("GET /orgs/"+orgid+"/agbots/"+agbotId+"/msgs") {
    Thread.sleep(1100)    // delay 1.1 seconds so 1 of the msgs will expire
    val response = Http(URL+"/agbots/"+agbotId+"/msgs").method("get").headers(ACCEPT).headers(AGBOTAUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val resp = parse(response.body).extract[GetAgbotMsgsResponse]
    assert(resp.messages.size === 3)
    var msg = resp.messages.find(m => m.message=="{msg1 from node1 to agbot1}").orNull
    assert(msg !== null)
    assert(msg.nodeId === orgnodeId)
    assert(msg.nodePubKey === "NODEABC")

    msg = resp.messages.find(m => m.message=="{msg2 from node1 to agbot1}").orNull
    assert(msg !== null)
    assert(msg.nodeId === orgnodeId)
    assert(msg.nodePubKey === "NODEABC")

    msg = resp.messages.find(m => m.message=="{msg1 from node2 to agbot1}").orNull
    assert(msg !== null)
    assert(msg.nodeId === orgnodeId2)
    assert(msg.nodePubKey === "NODE2ABC")
  }

  test("GET /orgs/"+orgid+"/agbots/"+agbotId2+"/msgs - then delete and get again") {
    var response = Http(URL+"/agbots/"+agbotId2+"/msgs").method("get").headers(ACCEPT).headers(AGBOT2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.OK)
    val resp = parse(response.body).extract[GetAgbotMsgsResponse]
    assert(resp.messages.size === 1)
    val msg = resp.messages.find(m => m.message == "{msg1 from node2 to agbot2}").orNull
    assert(msg !== null)
    assert(msg.nodeId === orgnodeId2)
    assert(msg.nodePubKey === "NODE2ABC")
    val msgId = msg.msgId

    response = Http(URL+"/agbots/"+agbotId2+"/msgs/"+msgId).method("delete").headers(ACCEPT).headers(AGBOT2AUTH).asString
    info("DELETE "+msgId+", code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.DELETED)

    response = Http(URL+"/agbots/"+agbotId2+"/msgs").method("get").headers(ACCEPT).headers(AGBOT2AUTH).asString
    info("code: "+response.code+", response.body: "+response.body)
    assert(response.code === HttpCode.NOT_FOUND)
    val resp2 = parse(response.body).extract[GetAgbotMsgsResponse]
    assert(resp2.messages.size === 0)
  }

  test("POST /orgs/"+orgid+"/agbots/"+agbotId+"/msgs - with low maxMessagesInMailbox") {
    if (runningLocally) {     // changing limits via POST /admin/config does not work in multi-node mode
      // Get the current config value so we can restore it afterward
      // ExchConfig.load  <-- already do this earlier
      val origMaxMessagesInMailbox = ExchConfig.getInt("api.limits.maxMessagesInMailbox")

      // Change the maxMessagesInMailbox config value in the svr
      var configInput = AdminConfigRequest("api.limits.maxMessagesInMailbox", "3")
      var response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)

      // Now try adding another msg - expect it to be rejected
      var input = PostAgbotsMsgsRequest("{msg1 from node1 to agbot1}", 300)
      response = Http(URL+"/agbots/"+agbotId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.ACCESS_DENIED)
      var apiResp = parse(response.body).extract[ApiResponse]
      assert(apiResp.msg.contains("Access Denied"))

      // But we should still be able to send a msg to agbot2, because his mailbox isn't full yet
      input = PostAgbotsMsgsRequest("{msg1 from node1 to agbot2}", 300)
      response = Http(URL+"/agbots/"+agbotId2+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(NODEAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.POST_OK)
      apiResp = parse(response.body).extract[ApiResponse]
      assert(apiResp.code === ApiResponseType.OK)

      response = Http(URL+"/agbots/"+agbotId2+"/msgs").method("get").headers(ACCEPT).headers(AGBOT2AUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.OK)
      val resp = parse(response.body).extract[GetAgbotMsgsResponse]
      assert(resp.messages.size === 1)

      // Restore the maxMessagesInMailbox config value in the svr
      configInput = AdminConfigRequest("api.limits.maxMessagesInMailbox", origMaxMessagesInMailbox.toString)
      response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)
    }
  }

  test("POST /orgs/"+orgid+"/nodes/"+nodeId+"/msgs - with low maxMessagesInMailbox") {
    if (runningLocally) {     // changing limits via POST /admin/config does not work in multi-node mode
      // Get the current config value so we can restore it afterward
      // ExchConfig.load  <-- already do this earlier
      val origMaxMessagesInMailbox = ExchConfig.getInt("api.limits.maxMessagesInMailbox")

      // Change the maxMessagesInMailbox config value in the svr
      var configInput = AdminConfigRequest("api.limits.maxMessagesInMailbox", "3")
      var response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)

      // Now try adding another msg - expect it to be rejected
      var input = PostNodesMsgsRequest("{msg1 from agbot1 to node1}", 300)
      response = Http(URL+"/nodes/"+nodeId+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.ACCESS_DENIED)
      var apiResp = parse(response.body).extract[ApiResponse]
      assert(apiResp.msg.contains("Access Denied"))

      // But we should still be able to send a msg to node2, because his mailbox isn't full yet
      input = PostNodesMsgsRequest("{msg1 from agbot1 to node2}", 300)
      response = Http(URL+"/nodes/"+nodeId2+"/msgs").postData(write(input)).method("post").headers(CONTENT).headers(ACCEPT).headers(AGBOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.POST_OK)
      apiResp = parse(response.body).extract[ApiResponse]
      assert(apiResp.code === ApiResponseType.OK)

      response = Http(URL+"/nodes/"+nodeId2+"/msgs").method("get").headers(ACCEPT).headers(NODE2AUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.OK)
      val resp = parse(response.body).extract[GetNodeMsgsResponse]
      assert(resp.messages.size === 1)

      // Restore the maxMessagesInMailbox config value in the svr
      configInput = AdminConfigRequest("api.limits.maxMessagesInMailbox", origMaxMessagesInMailbox.toString)
      response = Http(NOORGURL+"/admin/config").postData(write(configInput)).method("put").headers(CONTENT).headers(ACCEPT).headers(ROOTAUTH).asString
      info("code: "+response.code+", response.body: "+response.body)
      assert(response.code === HttpCode.PUT_OK)
    }
  }

  //~~~~~ Break down ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

  test("Cleanup - DELETE everything and confirm they are gone") {
    deleteAllOrgs()
  }
}
