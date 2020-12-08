package ie.zalando.fabric.gateway.web.marshalling

import akka.http.scaladsl.model.Uri
import cats.data.{NonEmptyList => NEL}
import ie.zalando.fabric.gateway.models.{HttpModels, SynchDomain}
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
      svcPort <- c.downField("servicePort").as[Option[K8sServicePortIdentifier]]
    } yield
      FabricServiceDefinition(host.getOrElse("IN-PROGRESS"),
                              svcName.getOrElse("IN-PROGRESS"),
                              svcPort.getOrElse(SynchDomain.DefaultIngressServiceProtocol))

  implicit val decodeUri: Decoder[Uri] = (c: HCursor) =>
    for {
      host <- c.as[String]
    } yield Uri.from(host = host)

  implicit val decodeCorsSupport: Decoder[CorsConfig] = (c: HCursor) =>
    for {
      allowedOrigins <- c.downField("allowedOrigins").as[Set[Uri]]
      allowedHeaders <- c.downField("allowedHeaders").as[Set[String]]
    } yield CorsConfig(allowedOrigins, allowedHeaders)

  implicit val decodeCompressionConfig: Decoder[CompressionConfig] = (c: HCursor) =>
    for {
      compressionFactor <- c.downField("compressionFactor").as[Int]
      encoding          <- c.downField("encoding").as[String]
    } yield CompressionConfig(compressionFactor, encoding)

  implicit val decodeIngressDefinition: Decoder[IngressDefinition] = (c: HCursor) =>
    for {
      hostMappings <- c.downField("spec").downField("rules").as[Set[IngressBackend]]
      metadata     <- c.downField("metadata").as[IngressMetaData]
    } yield IngressDefinition(hostMappings, metadata)

  implicit val decodeIngressMetaData: Decoder[IngressMetaData] = (c: HCursor) =>
    for {
      annotations <- c.downField("annotations").as[Map[String, String]]
      name        <- c.downField("name").as[String]
      namespace   <- c.downField("namespace").as[String]
      labels      <- c.downField("labels").as[Option[Map[String, String]]]
    } yield
      IngressMetaData(
        SkipperRouteDefinition(DnsString(name), List.empty, List.empty, None, annotations),
        name,
        namespace,
        labels
    )

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
            case "INHERITED" => GlobalWhitelistConfigInherited
            case _           => Enabled
          }
          .getOrElse(Enabled)
    )

  implicit val decodeUserWhitelist: Decoder[EmployeeAccessConfig] = (c: HCursor) =>
    for {
      allowType    <- c.downField("type").as[Option[String]]
      allowedUsers <- c.downField("user-list").as[Option[Set[String]]]
    } yield {
      EmployeeAccessConfig(allowType.map(_.toLowerCase).getOrElse {
        allowedUsers match {
          case Some(users) if users.nonEmpty => "allow_list" // maintain backward compatability
          case _                             => "scoped_access"
        }
      } match {
        case "allow_all"  => AllowAll
        case "deny_all"   => DenyAll
        case "allow_list" => AllowList(allowedUsers.getOrElse(Set.empty))
        case _            => ScopedAccess
      })
  }

  implicit val decodeStaticRouteConfig: Decoder[StaticRouteConfig] = (c: HCursor) =>
    for {
      statusCode <- c.downField("status").as[Int]
      headers    <- c.downField("headers").as[Map[String, String]]
      body       <- c.downField("body").as[String]
    } yield {
      StaticRouteConfig(statusCode, headers, body)
  }

  implicit val decodePathGatewayConfig: Decoder[ActionAuthorizations] = (c: HCursor) =>
    for {
      requiredPrivileges <- c.downField("x-fabric-privileges").as[Option[NEL[String]]]
      rateLimit          <- c.downField("x-fabric-ratelimits").as[Option[RateLimitDetails]]
      serviceWhitelist   <- c.downField("x-fabric-whitelist").as[Option[WhitelistConfig]]
      employeeAccess     <- c.downField("x-fabric-employee-access").as[Option[EmployeeAccessConfig]]
      staticRouteConfig  <- c.downField("x-fabric-static-response").as[Option[StaticRouteConfig]]
    } yield
      ActionAuthorizations(
        requiredPrivileges.getOrElse(NEL.one(Skipper.ZalandoTokenId)),
        rateLimit,
        serviceWhitelist.getOrElse(WhitelistConfig(Set(), GlobalWhitelistConfigInherited)),
        employeeAccess.getOrElse(EmployeeAccessConfig(GlobalEmployeeConfigInherited)),
        staticRouteConfig
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
      employeeAccess           <- c.downField("x-fabric-employee-access").as[Option[EmployeeAccessConfig]]
      compressionConfig        <- c.downField("x-fabric-compression-support").as[Option[CompressionConfig]]
      paths                    <- c.downField("paths").as[Map[PathMatch, GatewayPathRestrictions]]
      serviceProvider          <- deriveServiceProvider(services, stackSetIntegrationState)
    } yield
      GatewaySpec(
        serviceProvider,
        admins.toSeq.flatten.toSet,
        whitelist.map(s => WhitelistConfig(s, Enabled)).getOrElse(WhitelistConfig(Set(), Disabled)),
        cors,
        employeeAccess.getOrElse(EmployeeAccessConfig(ScopedAccess)),
        compressionConfig,
        paths.mapValues(PathConfig)
    )

  implicit val decodeGatewayOwnership: Decoder[GatewayStatus] = (c: HCursor) =>
    for {
      numOwnedIngress   <- c.downField("num_owned_ingress").as[Int]
      ownedIngressNames <- c.downField("owned_ingress_names").as[Option[Set[String]]]
    } yield GatewayStatus(numOwnedIngress, ownedIngressNames.getOrElse(Set.empty))

  implicit val decodeK8sServicePortIdentifier: Decoder[K8sServicePortIdentifier] = (c: HCursor) => {
    c.value match {
      case s if s.isString => c.as[String].map(NamedServicePort.apply)
      case i if i.isNumber => c.as[Int].map(NumericServicePort.apply)
      case _               => Left(DecodingFailure(s"${c.value} was not successfully converted to a K8sServicePortIdentifier", Nil))
    }
  }

  implicit val decodeServiceDescription: Decoder[ServiceDescription] = (c: HCursor) =>
    for {
      name <- c.downField("backend").downField("serviceName").as[String]
      port <- c.downField("backend").downField("servicePort").as[K8sServicePortIdentifier]
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
      labels    <- c.downField("labels").as[Option[Map[String, String]]]
      annos     <- c.downField("annotations").as[Map[String, String]]
    } yield {
      val dnsCompliantGatewayName = DnsString
        .fromString(name)
        .getOrElse({
          val gwName = DnsString.dnsCompliantName(name)
          logger.warn(s"Gateway name [$name] is not DNS compliant. Using $gwName instead")
          gwName
        })
      GatewayMeta(dnsCompliantGatewayName, namespace, labels, annos)
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
      state   <- c.downField("children").downField("Ingress.networking.k8s.io/v1beta1").as[Option[NamedIngressDefinitions]]
    // TODO: Do we need to specifically check for both here. Think maybe yes
    } yield SynchRequest(gateway, state.getOrElse(Map()))

  implicit val encodeServiceDescription: Encoder[ServiceDescription] = (sd: ServiceDescription) => {
    Json.obj(
      "backend" -> Json.obj(
        ("serviceName", sd.name.asJson),
        ("servicePort", sd.portIdentifier.asJson)
      ),
      "pathType" -> Json.fromString("ImplementationSpecific")
    )
  }

  implicit val encodeK8sServicePortIdentifier: Encoder[K8sServicePortIdentifier] = (port: K8sServicePortIdentifier) => {
    port match {
      case NamedServicePort(namedPort)     => Json.fromString(namedPort)
      case NumericServicePort(numericPort) => Json.fromInt(numericPort)
    }
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
    Encoder.forProduct4("annotations", "namespace", "name", "labels")(meta =>
      (meta.routeDefinition, meta.namespace, meta.name, meta.labels))

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
        corsConfig,
        definedServiceCount.map(_.size).getOrElse(0)
    )

  implicit val decodeValidationCorsSupport: Decoder[ValidationCorsConfig] = (c: HCursor) =>
    for {
      allowedOrigins <- c.downField("allowedOrigins").as[Set[String]]
    } yield {
      ValidationCorsConfig(allowedOrigins)
  }

  implicit val encodeValidationStatus: Encoder[ValidationStatus] =
    Encoder.forProduct1("reason")(resp => resp.rejectionReasons.map(_.reason).mkString(", "))

  implicit val encodeValidationResponse: Encoder[ValidationResponse] =
    Encoder.forProduct3("uid", "allowed", "status")(resp => (resp.uid, resp.allowed, resp.result))

  implicit val encodeResponseWrapper: Encoder[AdmissionReviewResponseWrapper] =
    Encoder.forProduct3("kind", "apiVersion", "response")(resp => (resp.kind, resp.version, resp.response))
}
