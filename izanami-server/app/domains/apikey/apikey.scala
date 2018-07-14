package domains.apikey

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Flow, Source}
import domains.AuthorizedPattern.AuthorizedPattern
import domains.abtesting.VariantBinding
import domains.apikey.ApikeyStore.ApikeyKey
import domains.events.EventStore
import domains.{AuthInfo, AuthorizedPattern, ImportResult, Key}
import store.Result.{AppErrors, ErrorMessage}
import store.SourceUtils.SourceKV
import store._

import scala.concurrent.{ExecutionContext, Future}

case class Apikey(clientId: String, name: String, clientSecret: String, authorizedPattern: AuthorizedPattern)
    extends AuthInfo {
  override def isAllowed(auth: Option[AuthInfo]): Boolean =
    Key.isAllowed(authorizedPattern)(auth)
}

object Apikey {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import play.api.libs.json.Reads._

  private val reads: Reads[Apikey] = {
    import domains.AuthorizedPattern._
    (
      (__ \ 'clientId).read[String](pattern("^[@0-9\\p{L} .'-]+$".r)) and
      (__ \ 'name).read[String](pattern("^[@0-9\\p{L} .'-]+$".r)) and
      (__ \ 'clientSecret).read[String](pattern("^[@0-9\\p{L} .'-]+$".r)) and
      (__ \ 'authorizedPattern).read[AuthorizedPattern](AuthorizedPattern.reads)
    )(Apikey.apply _)
  }

  private val writes = {
    import domains.AuthorizedPattern._
    Json.writes[Apikey]
  }

  implicit val format = Format[Apikey](reads, writes)

  def isAllowed(pattern: String)(auth: Option[AuthInfo]) =
    Key.isAllowed(pattern)(auth)

  def importData(
      apikeyStore: ApikeyStore
  )(implicit ec: ExecutionContext): Flow[(String, JsValue), ImportResult, NotUsed] = {
    import cats.implicits._
    import AppErrors._

    Flow[(String, JsValue)]
      .map { case (s, json) => (s, json.validate[Apikey]) }
      .mapAsync(4) {
        case (_, JsSuccess(obj, _)) =>
          apikeyStore.create(Key(obj.clientId), obj) map {
            ImportResult.fromResult _
          }
        case (s, JsError(_)) =>
          FastFuture.successful(ImportResult.error(ErrorMessage("json.parse.error", s)))
      }
      .fold(ImportResult()) { _ |+| _ }
  }

}

trait ApikeyStore extends DataStore[ApikeyKey, Apikey]
object ApikeyStore {
  type ApikeyKey = Key

  def apply(jsonStore: JsonDataStore, eventStore: EventStore, system: ActorSystem): ApikeyStore =
    new ApikeyStoreImpl(jsonStore, eventStore, system)

}

class ApikeyStoreImpl(jsonStore: JsonDataStore, eventStore: EventStore, system: ActorSystem) extends ApikeyStore {
  import Apikey._
  import domains.events.Events._
  import store.Result._
  import system.dispatcher

  implicit val s  = system
  implicit val es = eventStore

  override def create(id: ApikeyKey, data: Apikey): Future[Result[Apikey]] =
    jsonStore.create(id, format.writes(data)).to[Apikey].andPublishEvent { r =>
      ApikeyCreated(id, r)
    }

  override def update(oldId: ApikeyKey, id: ApikeyKey, data: Apikey): Future[Result[Apikey]] =
    this.getById(oldId).flatMap {
      case Some(oldValue) =>
        jsonStore
          .update(oldId, id, format.writes(data))
          .to[Apikey]
          .andPublishEvent { r =>
            ApikeyUpdated(id, oldValue, r)
          }
      case None =>
        Future.successful(Result.errors(ErrorMessage("error.data.missing", oldId.key)))
    }

  override def delete(id: ApikeyKey): Future[Result[Apikey]] =
    jsonStore.delete(id).to[Apikey].andPublishEvent { r =>
      ApikeyDeleted(id, r)
    }

  override def deleteAll(patterns: Seq[String]): Future[Result[Done]] =
    jsonStore.deleteAll(patterns)

  override def getById(id: ApikeyKey): Future[Option[Apikey]] =
    jsonStore.getById(id).to[Apikey]

  override def getByIdLike(patterns: Seq[String], page: Int, nbElementPerPage: Int): Future[PagingResult[Apikey]] =
    jsonStore
      .getByIdLike(patterns, page, nbElementPerPage)
      .map(jsons => JsonPagingResult(jsons))

  override def getByIdLike(patterns: Seq[String]): Source[(Key, Apikey), NotUsed] =
    jsonStore.getByIdLike(patterns).readsKV[Apikey]

  override def count(patterns: Seq[String]): Future[Long] =
    jsonStore.count(patterns)
}
