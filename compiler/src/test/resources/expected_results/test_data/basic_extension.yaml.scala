package basic_extension.yaml
import org.scalacheck.Gen
import org.scalacheck.Arbitrary._

object definitionsGenerator {
    import definitions.ErrorModel
    import definitions.ExtendedErrorModel
    def createErrorModelGenerator = _generate(ErrorModelGenerator)
    def createExtendedErrorModelGenerator = _generate(ExtendedErrorModelGenerator)
    val ErrorModelGenerator =
        for {
        message <- arbitrary[String]
        code <- arbitrary[Int]
        } yield ErrorModel(message, code)
    
    val ExtendedErrorModelGenerator =
        for {
        message <- arbitrary[String]
        code <- arbitrary[Int]
        rootCause <- arbitrary[String]
        } yield ExtendedErrorModel(message, code, rootCause)
    
    def _generate[T](gen: Gen[T]) = (count: Int) => for (i <- 1 to count) yield gen.sample

    def _genMap[K,V](keyGen: Gen[K], valGen: Gen[V]): Gen[Map[K,V]] = for {
        keys <- Gen.containerOf[List,K](keyGen)
        values <- Gen.containerOfN[List,V](keys.size, valGen)
    } yield keys.zip(values).toMap
}