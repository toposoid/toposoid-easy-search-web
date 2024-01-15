/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import akka.actor.ActorSystem
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{IMAGE, SENTENCE, ToposoidUtils}
import com.ideal.linked.toposoid.deduction.common.FacadeForAccessNeo4J
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorIdentifier, FeatureVectorSearchResult, RegistContentResult, SingleFeatureVectorForSearch}
import com.ideal.linked.toposoid.knowledgebase.nlp.model.FeatureVector
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, Reference}
import com.ideal.linked.toposoid.knowledgebase.search.model.{InputImageForSearch, InputSentenceForSearch}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import com.typesafe.scalalogging.LazyLogging
import io.jvm.uuid.UUID

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json.{Json, OWrites, Reads, __}

import scala.concurrent.ExecutionContext

case class SearchResultNode(id:String, sentence:String, sentenceType:Int, similarity:Float, url:String)
object SearchResultNode {
  implicit val jsonWrites: OWrites[SearchResultNode] = Json.writes[SearchResultNode]
  implicit val jsonReads: Reads[SearchResultNode] = Json.reads[SearchResultNode]
}

case class SearchResultEdge(source:SearchResultNode, target:SearchResultNode, value:String)
object SearchResultEdge {
  implicit val jsonWrites: OWrites[SearchResultEdge] = Json.writes[SearchResultEdge]
  implicit val jsonReads: Reads[SearchResultEdge] = Json.reads[SearchResultEdge]
}

case class SearchResultEdges(analyzedEdges:List[SearchResultEdge])
object SearchResultEdges {
  implicit val jsonWrites: OWrites[SearchResultEdges] = Json.writes[SearchResultEdges]
  implicit val jsonReads: Reads[SearchResultEdges] = Json.reads[SearchResultEdges]
}

/**
 *
 * @param system
 * @param cc
 * @param ec
 */
@Singleton
class HomeController @Inject()(system: ActorSystem, cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) with LazyLogging{

  def searchSentence() = Action(parse.json) { request =>
    try {
      val json = request.body
      val inputSentenceForSearch:InputSentenceForSearch  = Json.parse(json.toString()).as[InputSentenceForSearch]

      val knowledge = Knowledge(
        sentence = inputSentenceForSearch.sentence,
        lang = inputSentenceForSearch.lang,
        extentInfoJson = "{}",
        isNegativeSentence = false,
        knowledgeForImages = List.empty[KnowledgeForImage])
      val vector = FeatureVectorizer.getSentenceVector(knowledge)
      val searchResultEdges = getGraphData(vector, SENTENCE.index)
      Ok(Json.toJson(searchResultEdges)).as(JSON)
    } catch {
      case e: Exception => {
        logger.error(e.toString(), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  def searchImage() = Action(parse.json) { request =>
    try {
      val json = request.body
      val inputImageForSearch:InputImageForSearch  = Json.parse(json.toString).as[InputImageForSearch]
      val reference = Reference(url = inputImageForSearch.url, surface = "", surfaceIndex = -1, isWholeSentence = true, originalUrlOrReference = inputImageForSearch.url)
      val imageReference = ImageReference(reference, 0, 0, 0, 0)
      val knowledgeForImage = KnowledgeForImage(UUID.random.toString , imageReference = imageReference)

      val updatedKnowledgeForImage = inputImageForSearch.isUploaded match {
        case true => knowledgeForImage
        case _ => uploadImage(knowledgeForImage) //upload temporary image
      }
      val vector = FeatureVectorizer.getImageVector(updatedKnowledgeForImage.imageReference.reference.url)
      val searchResultEdges = getGraphData(vector, IMAGE.index)
      Ok(Json.toJson(searchResultEdges)).as(JSON)
    } catch {
      case e: Exception => {
        logger.error(e.toString(), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  private def getGraphData(vector:FeatureVector, featureType:Int):SearchResultEdges= {
    val vectorDBInfo = featureType match {
      case IMAGE.index => (conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"),conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), conf.getString("TOPOSOID_IMAGE_VECTORDB_SEARCH_NUM_MAX"))
      case _ => (conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"),conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_SEARCH_NUM_MAX"))
    }
    val searchJson: String = Json.toJson(SingleFeatureVectorForSearch(vector = vector.vector, num = vectorDBInfo._3.toInt)).toString()
    val featureVectorSearchResultJson: String = ToposoidUtils.callComponent(searchJson, vectorDBInfo._1, vectorDBInfo._2, "search")
    val result = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
    val searchResult: (List[SearchResultNode], List[SearchResultEdge]) = (result.ids zip result.similarities).foldLeft(List.empty[SearchResultNode], List.empty[SearchResultEdge]) {
      (acc, x) => {
        //ノードの情報を全て取得
        val searchResultNodes = getAllNodeByPropositionIds(x._1, x._2)
        //エッジの情報を取得
        val searchResultEdges = getTrivialEdges(x._1, searchResultNodes:List[SearchResultNode])
        (acc._1:::searchResultNodes, acc._2 ::: searchResultEdges)
      }
    }
    //孤立したノードもエッジに追加
    SearchResultEdges(getNonTrivialEdges(searchResult))
  }

  private def getAllNodeByPropositionIds(featureVectorIdentifier: FeatureVectorIdentifier, similarity:Float):List[SearchResultNode] ={
    //ノードの情報を全て取得
    val query = "MATCH (n) WHERE n.propositionId='%s' RETURN n".format(featureVectorIdentifier.propositionId)
    val jsonStr = FacadeForAccessNeo4J.getCypherQueryResult(query, "x")
    val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]
    neo4jRecords.records.foldLeft(List.empty[SearchResultNode]) {
      (acc2, y) => {
        val searchInnerResult = y.foldLeft((List.empty[Option[SearchResultNode]])) {
          (acc3, z) => {
            val semiGlobalNode = z.value.semiGlobalNode match {
              case Some(a) => Option(SearchResultNode(id = a.nodeId, sentence = a.sentence, sentenceType = a.sentenceType, similarity = similarity, url = ""))
              case _ => None
            }
            val featureNode = z.value.featureNode match {
              case Some(a) => Option(SearchResultNode(id = a.featureId, sentence = "", sentenceType = -1, similarity = similarity, url = a.source))
              case _ => None
            }
            acc3 :+ semiGlobalNode :+ featureNode
          }
        }
        //ノード情報を整える
        val nodes = searchInnerResult.filterNot(_.isEmpty).map(_.get)
        acc2 ::: nodes
      }
    }
  }

  private def getTrivialEdges(featureVectorIdentifier: FeatureVectorIdentifier, searchResultNodes:List[SearchResultNode])={
    //エッジの情報を取得
    val query2 = "MATCH (n1)-[e]->(n2) WHERE n1.propositionId='%s' AND n2.propositionId='%s' RETURN n1,e,n2".format(featureVectorIdentifier.propositionId, featureVectorIdentifier.propositionId)
    val jsonStr2 = FacadeForAccessNeo4J.getCypherQueryResult(query2, "x")
    val neo4jRecords2: Neo4jRecords = Json.parse(jsonStr2).as[Neo4jRecords]
    var count = 0
    neo4jRecords2.records.foldLeft(List.empty[SearchResultEdge]) {
      (acc2, y) => {
        val n1NodeId = y.filter(_.key.equals("n1")).head.value.semiGlobalNode match {
          case Some(a) => a.nodeId
          case _ => y.filter(_.key.equals("n1")).head.value.featureNode match {
            case Some(b) => b.featureId
            case _ => ""
          }
        }
        val n2NodeId = y.filter(_.key.equals("n2")).head.value.semiGlobalNode match {
          case Some(a) => a.nodeId
          case _ => y.filter(_.key.equals("n2")).head.value.featureNode match {
            case Some(b) => b.featureId
            case _ => ""
          }
        }
        count += 1
        val searchInnerResult = y.foldLeft((List.empty[Option[(String, String, String)]])) {
          (acc3, z) => {
            val semiGlobalEdge = z.value.semiGlobalEdge match {
              case Some(a) => Option((n1NodeId, n2NodeId, a.logicType))
              case _ => None
            }
            val featureEdge = z.value.featureEdge match {
              case Some(a) => Option((n1NodeId, n2NodeId, ""))
              case _ => None
            }
            acc3 :+ semiGlobalEdge :+ featureEdge
          }
        }
        //エッジ情報を整える
        val edges = searchInnerResult.filterNot(_.isEmpty) map (_.get)
        acc2 ::: edges.map(z => {
          val sourceNodeList = searchResultNodes.filter(_.id.equals(z._1))
          val sourceNode = sourceNodeList.size match {
            case 0 => SearchResultNode(id = "", sentence = "", sentenceType = -1, similarity = -1, url = "")
            case _ => sourceNodeList.head
          }
          val targetNodeList = searchResultNodes.filter(_.id.equals(z._2))
          val targetNode = targetNodeList.size match {
            case 0 => SearchResultNode(id = "", sentence = "", sentenceType = -1, similarity = -1, url = "")
            case _ => targetNodeList.head
          }
          val logicType = z._3
          SearchResultEdge(source = sourceNode, target = targetNode, value = logicType)
        })
      }
    }
  }

  private def getNonTrivialEdges(searchResult:(List[SearchResultNode], List[SearchResultEdge])):List[SearchResultEdge] = {
    //孤立したノードもエッジに追加
    val nodeIdsContainedInEdges = (searchResult._2.map(_.source.id) ::: searchResult._2.map(_.target.id)).distinct
    val orphanedNodes = searchResult._1.filter(y => {
      nodeIdsContainedInEdges.size match {
        case 0 => true
        case _ => nodeIdsContainedInEdges.filter(z => {z.equals(y.id)}).size == 0
      }
    })

    orphanedNodes.foldLeft(searchResult._2) {
      (acc, x) => {
        val emptyNode = SearchResultNode(id = "", sentence = "", sentenceType = -1, similarity = -1, url = "")
        val orphanedNode = SearchResultNode(id = x.id, sentence = x.sentence, sentenceType = x.sentenceType, similarity = x.similarity, url = x.url)
        acc :+ SearchResultEdge(orphanedNode, emptyNode, "")
      }
    }

  }

  private def uploadImage(knowledgeForImage: KnowledgeForImage): KnowledgeForImage = {
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "uploadTemporaryImage")
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }

}
