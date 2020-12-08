package ie.zalando.fabric.gateway.web.tracing

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{extractRequest, mapRouteResult, provide}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import com.lightstep.tracer.jre.JRETracer
import com.lightstep.tracer.shared.Options
import ie.zalando.fabric.gateway.config.AppConfig
import io.opentracing.noop.NoopTracerFactory
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import io.opentracing.{Span, Tracer}
import org.slf4j.{Logger, LoggerFactory}

trait TracingSupport {

  private val ClusterNameEnvVarKey = "CLUSTER"
  private val log: Logger          = LoggerFactory.getLogger(classOf[TracingSupport])

  private val tracer: Tracer = AppConfig.tracingConfig match {
    case Left(failures) =>
      log.warn(s"Defaulting to No-Op tracer: $failures")

      NoopTracerFactory.create()
    case Right(tracingConfig) =>
      val options: Options = new Options.OptionsBuilder()
        .withAccessToken(tracingConfig.accessToken)
        .withCollectorHost(tracingConfig.collectorHost.toASCIIString)
        .withCollectorPort(tracingConfig.collectorPort)
        .withComponentName(tracingConfig.componentName)
        .withTag(ClusterNameEnvVarKey, sys.env.getOrElse(ClusterNameEnvVarKey, "unknown"))
        .build()

      new JRETracer(options)
  }

  GlobalTracer.registerIfAbsent(tracer)

  def trace(operationName: String): Directive1[Span] = {
    extractRequest.flatMap { req =>
      val span = tracer.buildSpan(operationName).start()
      mapRouteResult {
        case c: Complete =>
          val resp = c.response
          span.setTag("http.status_code", resp.status.intValue())
          span.setTag("http.url", req.effectiveUri(securedConnection = false).toString())
          span.setTag("http.method", req.method.value)
          span.finish()

          c
        case r: Rejected =>
          Tags.ERROR.set(span, true)
          span.setTag("rejections", r.rejections.toString())
          span.finish()

          r
      } & provide(span)
    }
  }
}

object TracingDirectives extends TracingSupport
