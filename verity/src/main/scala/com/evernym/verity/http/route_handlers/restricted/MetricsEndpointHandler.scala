package com.evernym.verity.http.route_handlers.restricted

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Directives.{complete, extractClientIP, extractRequest, get, handleExceptions, logRequestResult, parameters, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.evernym.verity.actor._
import com.evernym.verity.actor.metrics.CollectLibindyMetrics
import com.evernym.verity.http.common.CustomExceptionHandler._
import com.evernym.verity.http.route_handlers.HttpRouteWithPlatform
import com.evernym.verity.metrics.{AllNodeMetricsData, NodeMetricsData}
import com.evernym.verity.util.Util.strToBoolean

import scala.concurrent.Future

trait MetricsEndpointHandler { this: HttpRouteWithPlatform =>

  case class GetMetricQueryParam(offAllNodes: Boolean, filterCriteria: MetricsFilterCriteria)

  object GetMetricQueryParam {
    def apply(offAllNodes: String, filterCriteria: MetricsFilterCriteria): GetMetricQueryParam = {
      GetMetricQueryParam(strToBoolean(offAllNodes), filterCriteria)
    }
  }

  protected def fetchMetrics(gqp: GetMetricQueryParam): Future[Any] = {
    (platform.libIndyMetricsCollector ? CollectLibindyMetrics()).flatMap(_ => {
      if (gqp.offAllNodes) {
        platform.singletonParentProxy ? SendMetricsOfAllNodes(gqp.filterCriteria)
      } else {
        platform.nodeSingleton ? GetNodeMetrics(gqp.filterCriteria)
      }
    })
  }

  protected val metricsRoutes: Route =
    handleExceptions(exceptionHandler) {
      logRequestResult("agency-service") {
        pathPrefix("agency" / "internal" / "metrics") {
          extractRequest { implicit req =>
            extractClientIP { implicit remoteAddress =>
              checkIfInternalApiCalledFromAllowedIPAddresses(clientIpAddress)
              (get & pathEnd) {
                parameters('allNodes ? "N", 'includeMetadata ? "Y", 'includeTags ? "N", 'filtered ? "Y") {
                  (ofAllNodes, includeMetadata, includeTags, filtered) =>
                    complete {
                      val criteria = MetricsFilterCriteria(includeMetadata, includeTags, filtered)
                      fetchMetrics(GetMetricQueryParam(ofAllNodes, criteria)).map[ToResponseMarshallable] {
                        case anm: AllNodeMetricsData => handleExpectedResponse(anm)
                        case nm: NodeMetricsData => handleExpectedResponse(nm)
                        case e => handleUnexpectedResponse(e)
                      }

                    }
                }
              }
            }
          }
        }
      }
    }
}
