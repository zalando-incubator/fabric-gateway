package ie.zalando.fabric.gateway.web.marshalling

import akka.http.scaladsl.model.Uri
import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.HttpModels
import ie.zalando.fabric.gateway.models.HttpModels._
import ie.zalando.fabric.gateway.models.SynchDomain._
import ie.zalando.fabric.gateway.models.ValidationDomain.{ResourceDetails, ValidationCorsConfig}
import ie.zalando.fabric.gateway.service.SkipperConfig._
import io.circe._
import io.circe.syntax._
import org.slf4j.{Logger, LoggerFactory}

trait JsonModels {

  private val logger: Logger = LoggerFactory.getLogger("RequestUnmarshaller")

  implicit val printer: Printer = Printer.spaces2.copy(dropNullValues = true)

  implicit val pathKeyDecoder: KeyDecoder[PathMatch] = {
    case path: String if path.isEmpty => None
    case path: String                 => Some(PathMatch(path))
    case _                            => None
  }

  implicit val actionKeyDecoder: KeyDecoder[HttpVerb] = (key: String) =>
    key.toUpperCase match {
      case "GET"     => Some(Get)
      case "HEAD"    => Some(Head)
      case "OPTIONS" => Some(Options)
      case "PUT"     => Some(Put)
      case "POST"    => Some(Post)
      case "PATCH"   => Some(Patch)
      case "DELETE"  => Some(Delete)
      case "CONNECT" => Some(Connect)
      case _         => None
    }

  implicit val decodeFabricService: Decoder[FabricServiceDefinition] = (c: HCursor) =>
    for {
      host    <- c.downField("host").as[Option[String]]
      svcName <- c.downField("serviceName").as[Option[String]]
      svcPort <- c.downField("servicePort").as[Option[String]]
    } yield
      FabricServiceDefinition(host.getOrElse("IN-PROGRESS"),
        svcName.getOrElse("IN-PROGRESS"),
        svcPort.getOrElse(HttpModels.DefaultIngressServiceProtocol))

  implicit val decodeUri: Decoder[Uri] = (c: HCursor) =>
    for {
      host <- c.as[String]
    } yield Uri.from(host = host)

  implicit val decodeCorsSupport: Decoder[CorsConfig] = (c: HCursor) =>
    for {
      state          <- c.downField("state").as[CorsState]
      allowedOrigins <- c.downField("allowedOrigins").as[Set[Uri]]
    } yield {
      CorsConfig(state, allowedOrigins)
    }

  implicit val decodeCorsState: Decoder[CorsState] = (c: HCursor) =>
    for {
      maybeState <- c.as[Option[String]]
    } yield
      maybeState
        .map(_.toUpperCase.trim)
        .map {
          case "ENABLED"  => Enabled
          case "DISABLED" => Disabled
          case _          => Enabled
        }
        .getOrElse(Enabled)

  implicit val decodeAsDummyIngressDefinition: Decoder[IngressDefinition] = (_: HCursor) =>
    Right(
      IngressDefinition(
        Set(),
        IngressMetaData(SkipperRouteDefinition(DnsString.fromString("dummy").get, Nil, Nil, None), "dummy", "default")))

  implicit val decodeRateLimitDetails: Decoder[RateLimitDetails] = (c: HCursor) =>
    for {
      default      <- c.downField("default-rate").as[Int]
      period       <- c.downField("period").as[Option[String]]
      perUidLimits <- c.downField("target").as[Option[Map[String, Int]]]
    } yield
      RateLimitDetails(
        default,
        period
          .map { value =>
            value.toLowerCase match {
              case "minute" => PerMinute
              case "hour"   => PerHour
              case _ =>
                throw new IllegalArgumentException(
                  s"$value is not acceptable as a rate limit period. Acceptable values: minute, hour")
            }
          }
          .getOrElse(PerMinute),
        perUidLimits.getOrElse(Map())
      )

  implicit val decodeResourceWhitelist: Decoder[WhitelistConfig] = (c: HCursor) =>
    for {
      whitelistedServices <- c.downField("service-list").as[Set[String]]
      maybeState          <- c.downField("state").as[Option[String]]
    } yield
      WhitelistConfig(
        whitelistedServices,
        maybeState
          .map(_.toUpperCase.trim)
          .map {
            case "ENABLED"   => Enabled
            case "DISABLED"  => Disabled
            case "INHERITED" => Inherited
            case _           => Enabled
          }
          .getOrElse(Enabled)
      )

  implicit val decodeUserWhitelist: Decoder[EmployeeAccessConfig] = (c: HCursor) =>
    for {
      whitelistedServices <- c.downField("user-list").as[Set[String]]
    } yield EmployeeAccessConfig(whitelistedServices)

  implicit val decodePathGatewayConfig: Decoder[ActionAuthorizations] = (c: HCursor) =>
    for {
      requiredPrivileges <- c.downField("x-fabric-privileges").as[Option[NEL[String]]]
      rateLimit          <- c.downField("x-fabric-ratelimits").as[Option[RateLimitDetails]]
      serviceWhitelist   <- c.downField("x-fabric-whitelist").as[Option[WhitelistConfig]]
      employeeAccess     <- c.downField("x-fabric-employee-access").as[Option[EmployeeAccessConfig]]
    } yield
      ActionAuthorizations(
        requiredPrivileges.getOrElse(NEL.one(Skipper.ZalandoTokenId)),
        rateLimit,
        serviceWhitelist.getOrElse(WhitelistConfig(Set(), Inherited)),
        employeeAccess.getOrElse(EmployeeAccessConfig(Set()))
      )

  def deriveServiceProvider(fabricDefinedServices: Option[Set[FabricServiceDefinition]],
                            stacksetManagedServices: Option[StackSetProvidedServices]): Decoder.Result[ServiceProvider] = {
    fabricDefinedServices
      .map { fsds =>
        SchemaDefinedServices(fsds.map { fsd =>
          IngressBackend(fsd.host, Set(ServiceDescription(fsd.service, fsd.port)))
        })
      }
      .orElse(stacksetManagedServices) match {
      case Some(svcProvider) => Right(svcProvider)
      case None =>
        Left(DecodingFailure("Neither 'x-fabric-service' nor `x-external-service-provider` keys could be resolved", Nil))
    }
  }

  implicit val decodeStackSetProviderServices: Decoder[StackSetProvidedServices] = (c: HCursor) =>
    for {
      stackSetName <- c.downField("stackSetName").as[String]
      hosts        <- c.downField("hosts").as[Set[String]]
    } yield StackSetProvidedServices(hosts, stackSetName)

  implicit val decodeGatewaySpec: Decoder[GatewaySpec] = (c: HCursor) =>
    for {
      services                 <- c.downField("x-fabric-service").as[Option[Set[FabricServiceDefinition]]]
      stackSetIntegrationState <- c.downField("x-external-service-provider").as[Option[StackSetProvidedServices]]
      admins                   <- c.downField("x-fabric-admins").as[Option[Set[String]]]
      whitelist                <- c.downField("x-fabric-whitelist").as[Option[Set[String]]]
      cors                     <- c.downField("x-fabric-cors-support").as[Option[CorsConfig]]
      paths                    <- c.downField("paths").as[Map[PathMatch, GatewayPathRestrictions]]
      serviceProvider          <- deriveServiceProvider(services, stackSetIntegrationState)
    } yield
      GatewaySpec(
        serviceProvider,
        admins.toSeq.flatten.toSet,
        whitelist.map(s => WhitelistConfig(s, Enabled)).getOrElse(WhitelistConfig(Set(), Disabled)),
        cors.getOrElse(CorsConfig(Disabled, Set.empty)),
        paths.mapValues(PathConfig)
      )

  implicit val decodeGatewayOwnership: Decoder[GatewayStatus] = (c: HCursor) =>
    for {
      numOwnedIngress   <- c.downField("num_owned_ingress").as[Int]
      ownedIngressNames <- c.downField("owned_ingress_names").as[Option[Set[String]]]
    } yield GatewayStatus(numOwnedIngress, ownedIngressNames.getOrElse(Set.empty))

  implicit val decodeServiceDescription: Decoder[ServiceDescription] = (c: HCursor) =>
    for {
      name <- c.downField("serviceName").as[String]
      port <- c.downField("servicePort").as[String]
    } yield ServiceDescription(name, port)

  implicit val decodeIngressBackend: Decoder[IngressBackend] = (c: HCursor) =>
    for {
      host     <- c.downField("host").as[String]
      services <- c.downField("http").downField("paths").as[Set[ServiceDescription]]
    } yield IngressBackend(host, services)

  implicit val decodeStackSetIntegrationDetails: Decoder[StackSetIntegrationDetails] = (c: HCursor) =>
    for {
      annotations <- c.downField("annotations").as[Map[String, Json]].map(_.map { case (k, v) => (k, v.noSpaces) })
      rules       <- c.downField("rules").as[Set[IngressBackend]]
    } yield StackSetIntegrationDetails(annotations, rules)

  implicit val decodeGatewayMetadata: Decoder[GatewayMeta] = (c: HCursor) =>
    for {
      name      <- c.downField("name").as[String]
      namespace <- c.downField("namespace").as[String]
    } yield {
      val dnsCompliantGatewayName = DnsString
        .fromString(name)
        .getOrElse({
          val gwName = DnsString.dnsCompliantName(name)
          logger.warn(s"Gateway name [$name] is not DNS compliant. Using $gwName instead")
          gwName
        })
      GatewayMeta(dnsCompliantGatewayName, namespace)
    }

  implicit val decodeSynchParent: Decoder[ControlledGatewayResource] = (c: HCursor) =>
    for {
      status <- c.downField("status").as[Option[GatewayStatus]]
      spec   <- c.downField("spec").as[GatewaySpec]
      meta   <- c.downField("metadata").as[GatewayMeta]
    } yield ControlledGatewayResource(status, spec, meta)

  implicit val decodeSynchRequest: Decoder[SynchRequest] = (c: HCursor) =>
    for {
      gateway <- c.downField("parent").as[ControlledGatewayResource]
      state   <- c.downField("children").downField("Ingress.extensions/v1beta1").as[Option[NamedIngressDefinitions]]
    } yield SynchRequest(gateway, state.getOrElse(Map()))

  implicit val encodeServiceDescription: Encoder[ServiceDescription] = (sd: ServiceDescription) => {
    Json.obj(
      "backend" -> Json.obj(
        ("serviceName", sd.name.asJson),
        ("servicePort", sd.portIdentifier.asJson),
      )
    )
  }

  implicit val encodeIngressBackend: Encoder[Set[IngressBackend]] = (mappings: Set[IngressBackend]) => {
    Json.obj("rules" -> Json.arr(mappings.map { backend =>
      Json.obj(
        ("host", Json.fromString(backend.host)),
        ("http", Json.obj("paths" -> backend.services.asJson))
      )
    }.toSeq: _*))
  }

  implicit val encodeSkipperRouteDefn: Encoder[SkipperRouteDefinition] = (route: SkipperRouteDefinition) =>
    Json
      .obj(
        ("zalando.org/skipper-filter", filtersInSkipperFormat(route.filters).asJson),
        ("zalando.org/skipper-predicate", predicatesInSkipperFormat(route.predicates).asJson),
        ("zalando.org/skipper-routes", route.customRoute.map(customRouteInSkipperFormat).asJson)
      )
      .deepMerge(route.additionalAnnotations.asJson)

  implicit val encodeIngressMetaData: Encoder[IngressMetaData] =
    Encoder.forProduct3("annotations", "namespace", "name")(meta => (meta.routeDefinition, meta.namespace, meta.name))

  implicit val encodeGatewayOwnershipState: Encoder[GatewayStatus] =
    Encoder.forProduct2("num_owned_ingress", "owned_ingress_names")(state => (state.numOwnedIngress, state.ownedIngress))

  implicit val encodeIngressDefinition: Encoder[IngressDefinition] =
    Encoder.forProduct4("kind", "spec", "apiVersion", "metadata")(id =>
      (HttpModels.IngressKind, id.hostMappings, HttpModels.IngressApiVersion, id.metadata))

  implicit val encodeUser: Encoder[SynchResponse] =
    Encoder.forProduct2("status", "children")(resp => (resp.status, resp.desiredIngressDefinitions))

  implicit val decodeResourceDetails: Decoder[ResourceDetails] = (c: HCursor) =>
    c.as[Map[String, Map[HttpVerb, Json]]].map(paths => ResourceDetails(paths))

  implicit val decodeValidationRequest: Decoder[ValidationRequest] = (c: HCursor) =>
    for {
      uid         <- c.downField("request").downField("uid").as[String]
      optName     <- c.downField("request").downField("name").as[Option[String]]
      namespace   <- c.downField("request").downField("namespace").as[String]
      optResource <- c.downField("request").downField("object").downField("spec").downField("paths").as[Option[ResourceDetails]]
      stackSetIntegration <- c.downField("request")
        .downField("object")
        .downField("spec")
        .downField("x-external-service-provider")
        .as[Option[StackSetProvidedServices]]
      corsConfig <- c.downField("request")
        .downField("object")
        .downField("spec")
        .downField("x-fabric-cors-support")
        .as[Option[ValidationCorsConfig]]
      definedServiceCount <- c.downField("request")
        .downField("object")
        .downField("spec")
        .downField("x-fabric-service")
        .as[Option[Set[FabricServiceDefinition]]]
    } yield
      ValidationRequest(
        uid,
        optName.getOrElse("Un-named Gateway"),
        namespace,
        optResource.getOrElse(ResourceDetails(Map.empty)),
        stackSetIntegration.isDefined,
        corsConfig.getOrElse(ValidationCorsConfig(Disabled, Set.empty)),
        definedServiceCount.map(_.size).getOrElse(0)
      )

  implicit val decodeValidationCorsSupport: Decoder[ValidationCorsConfig] = (c: HCursor) =>
    for {
      state          <- c.downField("state").as[CorsState]
      allowedOrigins <- c.downField("allowedOrigins").as[Set[String]]
    } yield {
      ValidationCorsConfig(state, allowedOrigins)
    }

  implicit val encodeValidationStatus: Encoder[ValidationStatus] =
    Encoder.forProduct1("reason")(resp => resp.rejectionReasons.map(_.reason).mkString(", "))

  implicit val encodeValidationResponse: Encoder[ValidationResponse] =
    Encoder.forProduct3("uid", "allowed", "status")(resp => (resp.uid, resp.allowed, resp.result))

  implicit val encodeResponseWrapper: Encoder[AdmissionReviewResponseWrapper] =
    Encoder.forProduct3("kind", "apiVersion", "response")(resp => (resp.kind, resp.version, resp.response))
}