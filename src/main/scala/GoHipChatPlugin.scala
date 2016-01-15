package localhost.GoHipChat

import com.thoughtworks.go.plugin.api.{GoPlugin,GoPluginIdentifier,GoApplicationAccessor}
import com.thoughtworks.go.plugin.api.annotation.Extension
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse
import com.thoughtworks.go.plugin.api.logging.Logger

object GoHipChatPlugin {
  import scala.collection.JavaConverters._
  import com.typesafe.config.ConfigFactory
  import java.io.File

  val goExtensionType = "notification"
  val supportedGoVersions = List("1.0").asJava
  
  val conf = ConfigFactory.parseFile(new File("/var/go/application.conf"))
    .withFallback(ConfigFactory.load(getClass().getClassLoader()))
  conf.checkValid(ConfigFactory.defaultReference(), "GoHipChat")

  val hipchatApiUrl = conf.getString("GoHipChat.roomUrl")
  val hipchatApiToken = conf.getString("GoHipChat.apiToken")
  val goBaseUrl = conf.getString("GoHipChat.goBaseUrl")
  
  val logger = Logger.getLoggerFor(classOf[GoHipChatPlugin])
  logger.info("finished init")
  logger.info(conf.root().render())
}

@Extension
class GoHipChatPlugin extends GoPlugin {
  import GoHipChatPlugin._
  import org.json4s.JsonDSL._
  import org.json4s.native.JsonMethods._
  import org.json4s.DefaultFormats
  import scalaj.http._
  import com.typesafe.config.ConfigFactory

  override def handle(requestMessage: GoPluginApiRequest):
    GoPluginApiResponse = {

      requestMessage.requestName match {
        case "notifications-interested-in" => handleNotificationsInterestedIn
        case "stage-status" => handleMessage(requestMessage)
        case _ => null
      }
  }

  def handleNotificationsInterestedIn: GoPluginApiResponse = {
    val msg = ("notifications" -> List("stage-status"))

    new GoPluginApiResponse() {
      val responseCode = 200
      val responseHeaders = null
      val responseBody = compact(render(msg))
    }
  }

  case class GoPipeline(
    name: String,
    group: String,
    stage: GoStage,
    counter: String)

  case class GoStage(
    name: String,
    state: String,
    counter: String)

  case class GoStageStatusMessage(pipeline: GoPipeline)

  def handleMessage(goPluginApiRequest: GoPluginApiRequest):
    GoPluginApiResponse = {
      logger.info("handleMessage")

      implicit val formats = DefaultFormats
      val json = parse(goPluginApiRequest.requestBody())
      val goStageStatusMessage = json.extract[GoStageStatusMessage]

      val pipeline = goStageStatusMessage.pipeline.name
      val stage = goStageStatusMessage.pipeline.stage.name
      val stageCounter = goStageStatusMessage.pipeline.stage.counter
      val pipelineCounter = goStageStatusMessage.pipeline.counter

      val stageName = s"$pipeline/$pipelineCounter/$stage/$stageCounter"
      val pipelineUrl = s"$goBaseUrl/pipelines/$stageName"

      val status = goStageStatusMessage.pipeline.stage.state
      
      val message = s"""[$stageName] is $status (<a href=\"$pipelineUrl\">details</a>)"""

      val from = java.net.InetAddress.getLocalHost().getHostName()
      
      val hipchatMsg = status match {
        case "Passed" => 
          ("color" -> "green") ~
          ("from" -> from) ~
          ("message_format" -> "html") ~
          ("message" -> message)
        case "Failed" => 
          ("color" -> "red") ~
          ("from" -> from) ~
          ("message_format" -> "html") ~
          ("message" -> message)
        case _ => 
          ("color" -> "gray") ~
          ("from" -> from) ~
          ("message_format" -> "html") ~
          ("message" -> message)
      }
          
      val hipchat = Http(hipchatApiUrl)
        .header("Authorization", s"Bearer $hipchatApiToken")
        .header("content-type", "application/json")
        .postData(compact(render(hipchatMsg))).asString

      val msg = ("status" -> "success")

      new GoPluginApiResponse() {
        val responseCode = 200
        val responseHeaders = null
        val responseBody = compact(render(msg))
      }
  }

  override def initializeGoApplicationAccessor(
    goApplicationAccessor: GoApplicationAccessor): Unit = {
      logger.info("initializeGoApplicationAccessor")
      // nothing
  }

  override def pluginIdentifier: GoPluginIdentifier = {
    logger.info("pluginIdentifier")
    new GoPluginIdentifier(goExtensionType, supportedGoVersions)
  }
}
