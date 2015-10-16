package de.zalando.swagger

import de.zalando.apifirst.Domain
import Domain._
import de.zalando.swagger.strictModel.ObjectValidation.SchemaOrBoolean
import de.zalando.swagger.strictModel._
import typeMetaConverter._

import scala.language.{postfixOps, implicitConversions}

/**
 * @author  slasch 
 * @since   14.10.2015.
 */
/*
  TODOs:

  - collectionFormat is ignored, but it's needed at runtime for parsing
  - No validation information
 */
object typeConverter {

  val PATH_SEPARATOR = "/"

  private type TypeConstructor = TypeMeta => Type
  private type TypeConstructors = Seq[TypeConstructor]
  type Types = Seq[Type]
  type NamedType = (String, Type)
  type NamedTypes = Seq[NamedType]

  @throws[MatchError]
  private implicit def fromParameterType(tpe: (ParameterType.Value, String)): TypeConstructor =
    (tpe._1, Option(tpe._2)) match {
      case (ParameterType.INTEGER, Some("int64"))     => Domain.Lng
      case (ParameterType.INTEGER, Some("int32"))     => Domain.Int
      case (ParameterType.INTEGER, _)                 => Domain.Int
      case (ParameterType.NUMBER, Some("float"))      => Domain.Flt
      case (ParameterType.NUMBER, Some("double"))     => Domain.Dbl
      case (ParameterType.NUMBER, _)                  => Domain.Dbl
      case (ParameterType.BOOLEAN, _)                 => Domain.Bool
      case (ParameterType.STRING, Some("byte"))       => Domain.Byt
      case (ParameterType.STRING, Some("date"))       => Domain.Date
      case (ParameterType.STRING, Some("date-time"))  => Domain.DateTime
      case (ParameterType.STRING, Some("password"))   => Domain.Password
      case (ParameterType.STRING, fmt)                => Domain.Str.curried(fmt)
      case (ParameterType.FILE, _)                    => Domain.File
    }

  @throws[MatchError]
  private implicit def fromPrimitiveType(tpe: (PrimitiveType.Val, String)): TypeConstructor =
    (tpe._1, Option(tpe._2)) match {
      case (PrimitiveType.INTEGER, Some("int64"))     => Domain.Lng
      case (PrimitiveType.INTEGER, Some("int32"))     => Domain.Int
      case (PrimitiveType.INTEGER, _)                 => Domain.Int
      case (PrimitiveType.NUMBER, Some("float"))      => Domain.Flt
      case (PrimitiveType.NUMBER, Some("double"))     => Domain.Dbl
      case (PrimitiveType.NUMBER, _)                  => Domain.Dbl
      case (PrimitiveType.BOOLEAN, _)                 => Domain.Bool
      case (PrimitiveType.STRING, Some("byte"))       => Domain.Byt
      case (PrimitiveType.STRING, Some("date"))       => Domain.Date
      case (PrimitiveType.STRING, Some("date-time"))  => Domain.DateTime
      case (PrimitiveType.STRING, Some("password"))   => Domain.Password
      case (PrimitiveType.STRING, fmt)                => Domain.Str.curried(fmt)
      case (PrimitiveType.NULL, _)                    => Domain.Null
      // TODO object, array
    }

  private def wrapInArray(t: NamedType): NamedType = t._1 -> Domain.Arr(t._2, t._2.meta)

  private def wrapInOption(t: NamedType): NamedType = t._1 -> Domain.Opt(t._2, t._2.meta)

  private def wrapInCatchAll(t: NamedTypes): NamedTypes = (t.head._1 -> Domain.CatchAll(t.head._2, t.head._2.meta)) +: t.tail

  def fromModel(model: strictModel.SwaggerModel): NamedTypes =
    fromDefinitions(model.definitions) ++ fromPaths(model.paths) ++ fromParameters(model.parameters)

  def fromParameters(parameters: ParameterDefinitions): NamedTypes =
    Option(parameters).toSeq.flatten flatMap {  p => fromParamListItem("parameters" + PATH_SEPARATOR + p._1, p._2) }

  def fromDefinitions(definitions: Definitions): NamedTypes =
    Option(definitions).toSeq.flatten flatMap { d => fromSchema("definitions" + PATH_SEPARATOR + d._1, d._2, Nil) }

  def fromPaths(paths: Paths): NamedTypes =
    Option(paths).toSeq.flatten flatMap { case (name, pathItem) =>
      pathItem.params(name + "/")
    } flatMap fromParamListItem

  implicit def fromParamListItem[T](nameAndParam: (String, ParametersListItem)): NamedTypes = {
    val (name, param) = nameAndParam
    param match {
      case r @ JsonReference(ref)             => fromReference(name, ref)
      case nb: NonBodyParameterCommons[_, _]  => fromNonBodyParameter(name, nb)
      case bp: BodyParameter[_]               => fromBodyParameter(bp)
    }
  }

  implicit def fromNonBodyParameter[T, CF](name: String, param: NonBodyParameterCommons[T, CF]): NamedTypes = {
    val meta = TypeMeta(Option(param.format))
    val fullName = name + PATH_SEPARATOR + param.name
    val result = if (param.isArray) wrapInArray(fromPrimitivesItems(fullName, param.items))
    else if (!param.required) wrapInOption(fullName -> (param.`type`, param.format)(meta))
    else fullName -> (param.`type`, param.format)(meta)
    Seq(result)
  }

  implicit def fromBodyParameter[T](param: BodyParameter[T]): NamedTypes = {
    fromSchemaOrReference(param.name, param.schema, if (param.required) Seq(param.name) else Nil)
  }

  implicit def fromSchemaOrReference(name: String, param: SchemaOrReference, required: Seq[String]): NamedTypes =
    param match {
      case p@JsonReference(ref) => fromReference(name, ref)
      case s: Schema[_]         => fromSchema(name, s, required)
      case f: FileSchema[_]     => fromFileSchema(f, required)
    }

  implicit def fromReference(name: String, ref: Ref): NamedTypes =
    Seq(name -> Domain.Reference(ref, TypeMeta(Option(ref))))

  implicit def fromPrimitivesItems[T](name: String, items: PrimitivesItems[T]): NamedType = {
    val meta = TypeMeta(Option(items.format))
    if (items.isArray)
      wrapInArray(fromPrimitivesItems(name, items.items))
    else
      name -> (items.`type`, items.format)(meta)
  }

  implicit def fromSchemaOrSchemaArray(name: String, param: SchemaOrSchemaArray): NamedTypes =
    param match {
      case sa: SchemaArray => fromSchemaArray(name, sa)
      case sr: SchemaOrReference => fromSchemaOrReference(name, sr, Nil)
    }

  implicit def fromSchemaArray(name: String, sa: SchemaArray): NamedTypes =
      sa flatMap { s => fromSchemaOrReference(name, s, Nil) }

  implicit def fromSchema[T](name: String, param: Schema[_], required: Seq[String]): NamedTypes = {
    val tpe = if (param.`type` != null) param.`type` else PrimitiveType.OBJECT
    tpe match {
      case t: ArrayJsonSchemaType => fromArrayJsonSchema(name, param, t)
      case p: PrimitiveType.Val => fromPrimitiveType(name, param, p, required)
    }
  }

  implicit def fromPrimitiveType[T](name: String, param: Schema[_], p: PrimitiveType.Val, required: Seq[String]): NamedTypes = p match {
    case PrimitiveType.ARRAY =>
      require(param.items.nonEmpty)
      val types = fromSchemaOrSchemaArray(name, param.items.get)
      wrapInArray(types.head) +: types.tail
    case PrimitiveType.OBJECT =>
      param.allOf map {
        extensionType(name)
      } getOrElse {
        val catchAll = fromSchemaOrBoolean(param.additionalProperties, param)
        val normal = fromSchemaProperties(param.properties, param.required)
        fromTypes(name, normal ++ catchAll.toSeq.flatten, Option(param.discriminator))
      }
    case _ =>
      val primitiveType = name -> (p, param.format)(param)
      if (isRequired(name, required)) Seq(primitiveType) else Seq(wrapInOption(primitiveType))
  }

  implicit def fromArrayJsonSchema[T](name: String, param: Schema[_], t: ArrayJsonSchemaType): NamedTypes =
    t.toSeq map PrimitiveType.fromString map {
      name -> fromPrimitiveType(_, param.format)(param)
    }

  implicit def fromSchemaProperties[T](param: SchemaProperties, required: Seq[String]): NamedTypes =
    Option(param).toSeq.flatten flatMap { p =>
      fromSchemaOrReference(p._1, p._2, required)
    }

  implicit def fromSchemaOrBoolean[T](param: SchemaOrBoolean, meta: TypeMeta): Option[NamedTypes] =
    Option(param) map {
      case b: Boolean => wrapInCatchAll(Seq("additionalProperties" -> Str(None, meta)))
      case s: SchemaOrReference => wrapInCatchAll(fromSchemaOrReference("additionalProperties", s, Nil))
    }

  implicit def extensionType[T](name: String)(schema: SchemaArray): NamedTypes =
    fromSchemaArray(name, schema)

  implicit def fromTypes(name: String, types: NamedTypes, discriminator: Option[String]): NamedTypes = {
    val extend = Nil // FIXME
    val fields = types map { t => Field(TypeName(t._1), t._2, TypeMeta(None)) }
    Seq(name -> Domain.TypeDef(TypeName(name), fields, extend, TypeMeta(None), discriminator))
  }

  implicit def fromFileSchema[T](schema: FileSchema[T], required: Seq[String]): NamedTypes = {
    ???
  }

  private def isRequired[T](name: String, required: Seq[String]): Boolean = {
    val isRequired = required != null && name.split(PATH_SEPARATOR).lastOption.exists(required.contains)
    isRequired
  }

}