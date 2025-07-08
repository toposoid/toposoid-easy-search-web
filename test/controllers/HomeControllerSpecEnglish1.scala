/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package controllers

import akka.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, PropositionRelation, Reference}
import com.ideal.linked.toposoid.knowledgebase.search.model.{InputImageForSearch, InputSentenceForSearch}
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import com.ideal.linked.toposoid.test.utils.TestUtils
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import controllers.TestUtilsEx.{getKnowledge, getTemporaryImageInfo, getUUID}
//import controllers.TestUtils.{getKnowledge, getTemporaryImageInfo, getUUID, registSingleClaim}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test._
import io.jvm.uuid.UUID
import scala.concurrent.duration.DurationInt

class HomeControllerSpecEnglish1 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
  }



  override implicit def defaultAwaitTimeout: Timeout = 600.seconds
  val controller: HomeController = inject[HomeController]

  val sentenceA = "There are two cats."
  val referenceA = Reference(url = "", surface = "cats", surfaceIndex = 3, isWholeSentence = false,
    originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
  val imageBoxInfoA = ImageBoxInfo(x =11 , y = 11, width = 466, height = 310)

  val sentenceB = "There is a dog."
  val referenceB = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
    originalUrlOrReference = "http://images.cocodataset.org/train2017/000000428746.jpg")
  val imageBoxInfoB = ImageBoxInfo(x = 0, y = 0, width = 0, height = 0)


  val lang = "en_US"
  "The specification1" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val knowledge = Knowledge(sentence = sentenceA, lang = lang, extentInfoJson = "{}", isNegativeSentence = false, knowledgeForImages = List.empty[KnowledgeForImage])
      val knowledgeForParser = KnowledgeForParser(propositionId = propositionId1, sentenceId = sentenceId1, knowledge = knowledge)
      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        premiseList = List.empty[KnowledgeForParser],
        premiseLogicRelation = List.empty[PropositionRelation],
        claimList = List(knowledgeForParser),
        claimLogicRelation = List.empty[PropositionRelation])
      TestUtils.registerData(knowledgeSentenceSetForParser, transversalState)


      val inputSentenceForSearch = InputSentenceForSearch(sentence = sentenceA, lang = lang, similarityThreshold = 0.85f)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchSentence")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.searchSentence(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val searchResultEdges: SearchResultEdges = Json.parse(jsonResult).as[SearchResultEdges]
      assert(searchResultEdges.analyzedEdges.size == 1)
      assert(searchResultEdges.analyzedEdges.filter(x => {
        x.source.sentence.equals(sentenceA) || x.target.sentence.equals(sentenceA)
      }).size == 1)
      TestUtils.deleteData(knowledgeSentenceSetForParser, transversalState)
    }
  }


  "The specification2" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA, transversalState)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])

      TestUtils.registerData(knowledgeSentenceSetForParser, transversalState)
      Thread.sleep(5000)

      val inputSentenceForSearch = InputSentenceForSearch(sentence = sentenceA, lang = lang, similarityThreshold = 0.85f)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchSentence")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson, TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.searchSentence(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val searchResultEdges: SearchResultEdges = Json.parse(jsonResult).as[SearchResultEdges]
      assert(searchResultEdges.analyzedEdges.size == 3)
      assert(searchResultEdges.analyzedEdges.filter(x => {
        x.source.sentence.equals(sentenceA) || x.target.sentence.equals(sentenceA)
      }).size == 1)
      assert(searchResultEdges.analyzedEdges.filter(x => {
        x.source.sentence.equals(sentenceB) || x.target.sentence.equals(sentenceB)
      }).size == 2)
      assert(searchResultEdges.analyzedEdges.filter(x => {
        x.source.sentence.equals("") || x.target.sentence.equals("")
      }).size == 2)
      assert(searchResultEdges.analyzedEdges.filter(x => {
        !x.source.url.equals("") || !x.target.url.equals("")
      }).size == 2)
      TestUtils.deleteData(knowledgeSentenceSetForParser, transversalState)
    }
  }

  "The specification3" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA, transversalState)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      TestUtils.registerData(knowledgeSentenceSetForParser, transversalState)

      val knowledge = Knowledge(sentence = sentenceA, lang = lang, extentInfoJson = "{}", isNegativeSentence = false, knowledgeForImages = List.empty[KnowledgeForImage])
      val knowledgeForParser = KnowledgeForParser(propositionId = propositionId2, sentenceId = sentenceId3, knowledge = knowledge)
      val knowledgeSentenceSetForParser2 = KnowledgeSentenceSetForParser(
        List.empty[KnowledgeForParser],
        List.empty[PropositionRelation],
        List(knowledgeForParser),
        List.empty[PropositionRelation])
      TestUtils.registerData(knowledgeSentenceSetForParser2, transversalState)

      val inputSentenceForSearch = InputSentenceForSearch(sentence = sentenceA, lang = lang, similarityThreshold = 0.85f)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchSentence")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson, TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.searchSentence(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val searchResultEdges: SearchResultEdges = Json.parse(jsonResult).as[SearchResultEdges]
      print(searchResultEdges)
      assert(searchResultEdges.analyzedEdges.size == 4)
      assert(searchResultEdges.analyzedEdges.filter(x => {
        x.source.sentence.equals(sentenceA) || x.target.sentence.equals(sentenceA)
      }).size == 2)
      assert(searchResultEdges.analyzedEdges.filter(x => {
        x.source.sentence.equals(sentenceB) || x.target.sentence.equals(sentenceB)
      }).size == 2)
    }
  }

  "The specification4" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA, transversalState)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      TestUtils.registerData(knowledgeSentenceSetForParser, transversalState)

      val inputSentenceForSearch = InputImageForSearch(url = "http://images.cocodataset.org/val2017/000000039769.jpg", lang = lang, similarityThreshold = 0.85f, false)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchImage")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson, TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.searchImage(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val searchResultEdges: SearchResultEdges = Json.parse(jsonResult).as[SearchResultEdges]
      print(searchResultEdges)
      assert(searchResultEdges.analyzedEdges.size == 3)
    }
  }

  "The specification5" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA, transversalState)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      TestUtils.registerData(knowledgeSentenceSetForParser, transversalState)

      val inputSentenceForSearch = InputImageForSearch(url = "http://images.cocodataset.org/train2017/000000428746.jpg", lang = lang, similarityThreshold = 0.85f, false)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchImage")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson, TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.searchImage(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val searchResultEdges: SearchResultEdges = Json.parse(jsonResult).as[SearchResultEdges]
      print(searchResultEdges)
      assert(searchResultEdges.analyzedEdges.size == 3)
    }
  }

  "The specification6" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA, transversalState)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB, transversalState)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      TestUtils.registerData(knowledgeSentenceSetForParser, transversalState)

      //Get TemporaryImage
      val knowledgeForImage: KnowledgeForImage = getTemporaryImageInfo(referenceB, imageBoxInfoB, transversalState)

      val inputSentenceForSearch = InputImageForSearch(url = knowledgeForImage.imageReference.reference.url, lang = lang, similarityThreshold = 0.85f, true)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchImage")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson, TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse(json))
      val result = call(controller.searchImage(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult: String = contentAsJson(result).toString()
      val searchResultEdges: SearchResultEdges = Json.parse(jsonResult).as[SearchResultEdges]
      print(searchResultEdges)
      assert(searchResultEdges.analyzedEdges.size == 3)
    }
  }
  //TODO:Add Test for Multiple Results
}
