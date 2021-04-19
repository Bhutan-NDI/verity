package com.evernym.verity.protocol.engine.asyncapi.segmentstorage

import com.evernym.verity.protocol.engine.segmentedstate.SegmentedStateTypes.{SegmentAddress, SegmentKey}

import scala.util.Try

trait SegmentStoreAccess {

  def storeSegment(segmentAddress: SegmentAddress,
                   segmentKey: SegmentKey,
                   segment: Any,
                   retentionPolicy: Option[String]) (handler: Try[StoredSegment] => Unit): Unit

  def withSegment[T](segmentAddress: SegmentAddress,
                     segmentKey: SegmentKey,
                     retentionPolicy: Option[String]) (handler: Try[Option[T]] => Unit): Unit
}

case class StoredSegment(segmentAddress: SegmentAddress, segment: Option[Any])
