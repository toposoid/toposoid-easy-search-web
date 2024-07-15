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

import akka.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.data.accessor.neo4j.Neo4JAccessor
import com.ideal.linked.toposoid.common.{TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeForImage, PropositionRelation, Reference}
import com.ideal.linked.toposoid.knowledgebase.search.model.{InputImageForSearch, InputSentenceForSearch}
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import controllers.TestUtils.{getKnowledge, getTemporaryImageInfo, getUUID, registSingleClaim}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test._

import scala.concurrent.duration.DurationInt

class HomeControllerSpecEnglish1 extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting {

  val transversalState:String = Json.toJson(TransversalState(username="guest")).toString()

  before {
    Neo4JAccessor.delete()
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", TransversalState(username="guest"))
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "createSchema", TransversalState(username="guest"))
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override def afterAll(): Unit = {
    Neo4JAccessor.delete()
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
      registSingleClaim(knowledgeForParser)

      val inputSentenceForSearch = InputSentenceForSearch(sentence = sentenceA, lang = lang, similarityThreshold = 0.85f)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchSentence")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalState, TRANSVERSAL_STATE.str -> transversalState)
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
    }
  }


  "The specification2" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      Thread.sleep(5000)

      val inputSentenceForSearch = InputSentenceForSearch(sentence = sentenceA, lang = lang, similarityThreshold = 0.85f)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchSentence")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalState, TRANSVERSAL_STATE.str -> transversalState)
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
    }
  }

  "The specification3" should {
    "returns an appropriate response" in {
      val propositionId1 = getUUID()
      val propositionId2 = getUUID()
      val sentenceId1 = getUUID()
      val sentenceId2 = getUUID()
      val sentenceId3 = getUUID()
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      Thread.sleep(5000)

      val knowledge = Knowledge(sentence = sentenceA, lang = lang, extentInfoJson = "{}", isNegativeSentence = false, knowledgeForImages = List.empty[KnowledgeForImage])
      val knowledgeForParser = KnowledgeForParser(propositionId = propositionId2, sentenceId = sentenceId3, knowledge = knowledge)
      registSingleClaim(knowledgeForParser)

      val inputSentenceForSearch = InputSentenceForSearch(sentence = sentenceA, lang = lang, similarityThreshold = 0.85f)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchSentence")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalState, TRANSVERSAL_STATE.str -> transversalState)
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
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      Thread.sleep(5000)

      val inputSentenceForSearch = InputImageForSearch(url = "http://images.cocodataset.org/val2017/000000039769.jpg", lang = lang, similarityThreshold = 0.85f, false)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchImage")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalState, TRANSVERSAL_STATE.str -> transversalState)
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
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      Thread.sleep(5000)

      val inputSentenceForSearch = InputImageForSearch(url = "http://images.cocodataset.org/train2017/000000428746.jpg", lang = lang, similarityThreshold = 0.85f, false)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchImage")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalState, TRANSVERSAL_STATE.str -> transversalState)
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
      val knowledgePremise = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      val knowledgeClaim = getKnowledge(lang = lang, sentence = sentenceB, reference = referenceB, imageBoxInfo = imageBoxInfoB)

      val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
        List(KnowledgeForParser(propositionId1, sentenceId1, knowledgePremise)),
        List.empty[PropositionRelation],
        List(KnowledgeForParser(propositionId1, sentenceId2, knowledgeClaim)),
        List.empty[PropositionRelation])
      Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      FeatureVectorizer.createVector(knowledgeSentenceSetForParser, TransversalState(username="guest"))
      Thread.sleep(5000)

      //Get TemporaryImage
      val knowledgeForImage: KnowledgeForImage = getTemporaryImageInfo(referenceB, imageBoxInfoB)

      val inputSentenceForSearch = InputImageForSearch(url = knowledgeForImage.imageReference.reference.url, lang = lang, similarityThreshold = 0.85f, true)
      val json = Json.toJson(inputSentenceForSearch).toString()
      val fr = FakeRequest(POST, "/searchImage")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalState, TRANSVERSAL_STATE.str -> transversalState)
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
