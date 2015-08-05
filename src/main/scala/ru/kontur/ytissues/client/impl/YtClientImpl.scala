package ru.kontur.ytissues.client.impl

import dispatch._
import org.json4s.JsonAST.JValue
import ru.kontur.ytissues.client._
import ru.kontur.ytissues.settings.YtSettings

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Try, Failure, Success}

/**
 * @author Michael Plusnin <michael.plusnin@gmail.com>
 * @since 04.08.2015
 */
class YtClientImpl(settings: YtSettings)(implicit ec: ExecutionContext) extends YtClient {
  val base = url(settings.url)
  val http = Http

  override def getIssue(id: String): Future[Option[Issue]] = {
    val request = (base / "rest" / "issue" / id)
      .addHeader("Accept", "application/json")
      .as_!(settings.user, settings.password)

    val x = http(request OK as.json4s.Json).either

    val p = Promise[Option[Issue]]()

    x onComplete {
      case Success(Right(json)) => p.complete(parse(json))
      case Success(Left(StatusCode(404))) => p.success(None)
      case Success(Left(t)) => p.failure(t)
      case Failure(t) => p.failure(t)
    }

    p.future
  }

  private def parse(json: JValue) : Try[Option[Issue]] = {
    import org.json4s._
    val idOpt = json \ "id" match {
      case JString(x) => Some(x)
      case _ => None
    }

    val summaryOpt: List[String] = for {
      JObject(field) <- json \ "field"
      if field contains ("name" -> JString("summary"))
      ("value", JString(s)) <- field
    } yield s

    val resolvedOpt: List[String] = for {
      JObject(field) <- json \ "field"
      if field contains ("name" -> JString("resolved"))
      ("value", JString(r)) <- field
    } yield r

    val status = resolvedOpt.headOption match {
      case Some(_) => Resolved
      case None => Opened
    }

    val parseOpt = for {
      id <- idOpt
      summary <- summaryOpt.headOption
    } yield Issue(id, summary, status)

    parseOpt match {
      case Some(x) => Success(Some(x))
      case None => Failure(new Exception("Can't parse issue"))
    }
  }
}