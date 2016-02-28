
package split.petstore.api.yaml

import play.api.http.Writeable
import de.zalando.play.controllers.{ResponseWritersBase, WriteableWrapper}
import WriteableWrapper.writeable2wrapper
import play.api.libs.iteratee.Execution.Implicits.trampoline

/**
* This is a place to define definitions of custom serializers for results.
* Serializers are just instances of {@Writeable}s
* They must be places into the {@custom} field of the ResponseWriters object
*
*/
object ResponseWriters extends ResponseWritersBase {

    /**
    * Transformer instance to be used as logic for {@Writeable}
    * It is important to define the type of {@Writeable} explicit and as narrow as possible
    * in order for play-swagger to be able to provide safety net for
    * different response types
    */
    val writable_application_xml_Null_esc: Writeable[Null] =
        Writeable(a => ???, Some("application/xml"))

    val writable_application_xml_PetsFindByStatusGetResponses200_esc: Writeable[PetsFindByStatusGetResponses200] =
        Writeable(a => ???, Some("application/xml"))

    val writable_application_xml_Order_esc: Writeable[Order] =
        Writeable(a => ???, Some("application/xml"))

    val writable_application_xml_User_esc: Writeable[User] =
        Writeable(a => ???, Some("application/xml"))

    val writable_application_xml_Pet_esc: Writeable[Pet] =
        Writeable(a => ???, Some("application/xml"))

    val writable_application_xml_String_esc: Writeable[String] =
        Writeable(a => ???, Some("application/xml"))

    /**
    * This collection contains all {@Writeable}s which could be used in
    * as a marshaller for different mime types and types of response
    */
    override val custom: Seq[WriteableWrapper[_]] = Seq(
        writable_application_xml_Null_esc, 
        writable_application_xml_PetsFindByStatusGetResponses200_esc, 
        writable_application_xml_Order_esc, 
        writable_application_xml_User_esc, 
        writable_application_xml_Pet_esc, 
        writable_application_xml_String_esc
    )
}
