package simple.petstore.api.yaml

import de.zalando.play.controllers._
import org.scalacheck._
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck.Test._
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.test._
import play.api.mvc.{QueryStringBindable, PathBindable}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import java.net.URLEncoder
import play.api.http.Writeable
import com.fasterxml.jackson.databind.ObjectMapper

import play.api.test.Helpers.{status => requestStatusCode_}
import play.api.test.Helpers.{contentAsString => requestContentAsString_}
import play.api.test.Helpers.{contentType => requestContentType_}
import de.zalando.play.controllers.ArrayWrapper

import Generators._

    @RunWith(classOf[JUnitRunner])
    class SimplePetstoreApiYamlSpec extends Specification {
        def toPath[T](value: T)(implicit binder: PathBindable[T]): String = Option(binder.unbind("", value)).getOrElse("")
        def toQuery[T](key: String, value: T)(implicit binder: QueryStringBindable[T]): String = Option(binder.unbind(key, value)).getOrElse("")
        def toHeader[T](value: T)(implicit binder: PathBindable[T]): String = Option(binder.unbind("", value)).getOrElse("")

      def checkResult(props: Prop) =
        Test.check(Test.Parameters.default, props).status match {
          case Failed(_, labels) => failure(labels.mkString("\n"))
          case Proved(_) | Exhausted | Passed => success
          case PropException(_, e, labels) =>
            val error = if (labels.isEmpty) e.getLocalizedMessage() else labels.mkString("\n")
            failure(error)
        }

      private def parserConstructor(mimeType: String) = PlayBodyParsing.jacksonMapper(mimeType)

      def parseResponseContent[T](mapper: ObjectMapper, content: String, mimeType: Option[String], expectedType: Class[T]) =
        mapper.readValue(content, expectedType)


    "POST /api/pets" should {
        def testInvalidInput(pet: NewPet) = {


            val url = s"""/api/pets"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                    Seq() :+ ("Accept" -> acceptHeader)

                    val parsed_pet = PlayBodyParsing.jacksonMapper("ErrorModel").writeValueAsString(pet)

                val path = route(FakeRequest(POST, url).withHeaders(headers:_*).withBody(parsed_pet)).get
                val errors = new PetsPostValidator(pet).errors

                lazy val validations = errors flatMap { _.messages } map { m => contentAsString(path).contains(m) ?= true }

                ("given 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" + "and body [" + parsed_pet + "]") |: all(
                    requestStatusCode_(path) ?= BAD_REQUEST ,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.nonEmpty ?= true,
                    all(validations:_*)
                )
            }
            propertyList.reduce(_ && _)
        }
        def testValidInput(pet: NewPet) = {
            
            val parsed_pet = parserConstructor("ErrorModel").writeValueAsString(pet)
            
            val url = s"""/api/pets"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                   Seq() :+ ("Accept" -> acceptHeader)
                val path = route(FakeRequest(POST, url).withHeaders(headers:_*).withBody(parsed_pet)).get
                val errors = new PetsPostValidator(pet).errors
                val possibleResponseTypes: Map[Int,Class[_ <: Any]] = Map(
                    200 -> classOf[Pet]
                )

                val expectedCode = requestStatusCode_(path)
                val mimeType = requestContentType_(path)
                val mapper = parserConstructor(mimeType.getOrElse("application/json"))

                val parsedApiResponse = scala.util.Try {
                    parseResponseContent(mapper, requestContentAsString_(path), mimeType, possibleResponseTypes(expectedCode))
                }

                ("given response code " + expectedCode + " and 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" + "and body [" + parsed_pet + "]") |: all(
                    possibleResponseTypes.contains(expectedCode) ?= true,
                    parsedApiResponse.isSuccess ?= true,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.isEmpty ?= true
                )
            }
            propertyList.reduce(_ && _)
        }
        "discard invalid data" in new WithApplication {
            val genInputs = for {
                    pet <- NewPetGenerator
                } yield pet
            val inputs = genInputs suchThat { pet =>
                new PetsPostValidator(pet).errors.nonEmpty
            }
            val props = forAll(inputs) { i => testInvalidInput(i) }
            checkResult(props)
        }
        "do something with valid data" in new WithApplication {
            val genInputs = for {
                pet <- NewPetGenerator
            } yield pet
            val inputs = genInputs suchThat { pet =>
                new PetsPostValidator(pet).errors.isEmpty
            }
            val props = forAll(inputs) { i => testValidInput(i) }
            checkResult(props)
        }

    }

    "GET /api/pets" should {
        def testInvalidInput(input: (PetsGetTags, PetsGetLimit)) = {

            val (tags, limit) = input

            val url = s"""/api/pets?${toQuery("tags", tags)}&${toQuery("limit", limit)}"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                    Seq() :+ ("Accept" -> acceptHeader)


                val path = route(FakeRequest(GET, url).withHeaders(headers:_*)).get
                val errors = new PetsGetValidator(tags, limit).errors

                lazy val validations = errors flatMap { _.messages } map { m => contentAsString(path).contains(m) ?= true }

                ("given 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" ) |: all(
                    requestStatusCode_(path) ?= BAD_REQUEST ,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.nonEmpty ?= true,
                    all(validations:_*)
                )
            }
            propertyList.reduce(_ && _)
        }
        def testValidInput(input: (PetsGetTags, PetsGetLimit)) = {
            val (tags, limit) = input
            
            val url = s"""/api/pets?${toQuery("tags", tags)}&${toQuery("limit", limit)}"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                   Seq() :+ ("Accept" -> acceptHeader)
                val path = route(FakeRequest(GET, url).withHeaders(headers:_*)).get
                val errors = new PetsGetValidator(tags, limit).errors
                val possibleResponseTypes: Map[Int,Class[_ <: Any]] = Map(
                    200 -> classOf[Seq[Pet]]
                )

                val expectedCode = requestStatusCode_(path)
                val mimeType = requestContentType_(path)
                val mapper = parserConstructor(mimeType.getOrElse("application/json"))

                val parsedApiResponse = scala.util.Try {
                    parseResponseContent(mapper, requestContentAsString_(path), mimeType, possibleResponseTypes(expectedCode))
                }

                ("given response code " + expectedCode + " and 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" ) |: all(
                    possibleResponseTypes.contains(expectedCode) ?= true,
                    parsedApiResponse.isSuccess ?= true,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.isEmpty ?= true
                )
            }
            propertyList.reduce(_ && _)
        }
        "discard invalid data" in new WithApplication {
            val genInputs = for {
                        tags <- PetsGetTagsGenerator
                        limit <- PetsGetLimitGenerator
                    
                } yield (tags, limit)
            val inputs = genInputs suchThat { case (tags, limit) =>
                new PetsGetValidator(tags, limit).errors.nonEmpty
            }
            val props = forAll(inputs) { i => testInvalidInput(i) }
            checkResult(props)
        }
        "do something with valid data" in new WithApplication {
            val genInputs = for {
                    tags <- PetsGetTagsGenerator
                    limit <- PetsGetLimitGenerator
                
            } yield (tags, limit)
            val inputs = genInputs suchThat { case (tags, limit) =>
                new PetsGetValidator(tags, limit).errors.isEmpty
            }
            val props = forAll(inputs) { i => testValidInput(i) }
            checkResult(props)
        }

    }

    "GET /api/pets/{id}" should {
        def testInvalidInput(id: Long) = {


            val url = s"""/api/pets/${toPath(id)}"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                    Seq() :+ ("Accept" -> acceptHeader)


                val path = route(FakeRequest(GET, url).withHeaders(headers:_*)).get
                val errors = new PetsIdGetValidator(id).errors

                lazy val validations = errors flatMap { _.messages } map { m => contentAsString(path).contains(m) ?= true }

                ("given 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" ) |: all(
                    requestStatusCode_(path) ?= BAD_REQUEST ,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.nonEmpty ?= true,
                    all(validations:_*)
                )
            }
            propertyList.reduce(_ && _)
        }
        def testValidInput(id: Long) = {
            
            val url = s"""/api/pets/${toPath(id)}"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                   Seq() :+ ("Accept" -> acceptHeader)
                val path = route(FakeRequest(GET, url).withHeaders(headers:_*)).get
                val errors = new PetsIdGetValidator(id).errors
                val possibleResponseTypes: Map[Int,Class[_ <: Any]] = Map(
                    200 -> classOf[Pet]
                )

                val expectedCode = requestStatusCode_(path)
                val mimeType = requestContentType_(path)
                val mapper = parserConstructor(mimeType.getOrElse("application/json"))

                val parsedApiResponse = scala.util.Try {
                    parseResponseContent(mapper, requestContentAsString_(path), mimeType, possibleResponseTypes(expectedCode))
                }

                ("given response code " + expectedCode + " and 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" ) |: all(
                    possibleResponseTypes.contains(expectedCode) ?= true,
                    parsedApiResponse.isSuccess ?= true,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.isEmpty ?= true
                )
            }
            propertyList.reduce(_ && _)
        }
        "discard invalid data" in new WithApplication {
            val genInputs = for {
                    id <- LongGenerator
                } yield id
            val inputs = genInputs suchThat { id =>
                new PetsIdGetValidator(id).errors.nonEmpty
            }
            val props = forAll(inputs) { i => testInvalidInput(i) }
            checkResult(props)
        }
        "do something with valid data" in new WithApplication {
            val genInputs = for {
                id <- LongGenerator
            } yield id
            val inputs = genInputs suchThat { id =>
                new PetsIdGetValidator(id).errors.isEmpty
            }
            val props = forAll(inputs) { i => testValidInput(i) }
            checkResult(props)
        }

    }

    "DELETE /api/pets/{id}" should {
        def testInvalidInput(id: Long) = {


            val url = s"""/api/pets/${toPath(id)}"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                    Seq() :+ ("Accept" -> acceptHeader)


                val path = route(FakeRequest(DELETE, url).withHeaders(headers:_*)).get
                val errors = new PetsIdDeleteValidator(id).errors

                lazy val validations = errors flatMap { _.messages } map { m => contentAsString(path).contains(m) ?= true }

                ("given 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" ) |: all(
                    requestStatusCode_(path) ?= BAD_REQUEST ,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.nonEmpty ?= true,
                    all(validations:_*)
                )
            }
            propertyList.reduce(_ && _)
        }
        def testValidInput(id: Long) = {
            
            val url = s"""/api/pets/${toPath(id)}"""
            val acceptHeaders = Seq(
               "application/json"
            )
            val propertyList = acceptHeaders.map { acceptHeader =>
                val headers =
                   Seq() :+ ("Accept" -> acceptHeader)
                val path = route(FakeRequest(DELETE, url).withHeaders(headers:_*)).get
                val errors = new PetsIdDeleteValidator(id).errors
                val possibleResponseTypes: Map[Int,Class[_ <: Any]] = Map(
                    204 -> classOf[Null]
                )

                val expectedCode = requestStatusCode_(path)
                val mimeType = requestContentType_(path)
                val mapper = parserConstructor(mimeType.getOrElse("application/json"))

                val parsedApiResponse = scala.util.Try {
                    parseResponseContent(mapper, requestContentAsString_(path), mimeType, possibleResponseTypes(expectedCode))
                }

                ("given response code " + expectedCode + " and 'Accept' header '" + acceptHeader + "' and URL: [" + url + "]" ) |: all(
                    possibleResponseTypes.contains(expectedCode) ?= true,
                    parsedApiResponse.isSuccess ?= true,
                    requestContentType_(path) ?= Some(acceptHeader),
                    errors.isEmpty ?= true
                )
            }
            propertyList.reduce(_ && _)
        }
        "discard invalid data" in new WithApplication {
            val genInputs = for {
                    id <- LongGenerator
                } yield id
            val inputs = genInputs suchThat { id =>
                new PetsIdDeleteValidator(id).errors.nonEmpty
            }
            val props = forAll(inputs) { i => testInvalidInput(i) }
            checkResult(props)
        }
        "do something with valid data" in new WithApplication {
            val genInputs = for {
                id <- LongGenerator
            } yield id
            val inputs = genInputs suchThat { id =>
                new PetsIdDeleteValidator(id).errors.isEmpty
            }
            val props = forAll(inputs) { i => testValidInput(i) }
            checkResult(props)
        }

    }
}
