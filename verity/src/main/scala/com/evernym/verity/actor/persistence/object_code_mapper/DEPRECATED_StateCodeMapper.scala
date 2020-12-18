package com.evernym.verity.actor.persistence.object_code_mapper

import com.evernym.verity.actor.{ItemManagerState, ResourceUsageState}
import scalapb.GeneratedMessageCompanion

/**
 * need to keep this for backward compatibility
 *
 * mostly ItemManager actor would have stored new snapshot for sure and
 * in that case this mapping won't be required for it
 * but we can't be sure if all ResourceUsageTracker actor instances
 * would have got chance (it depends on users hitting requests) to save newer snapshots
 */
object DEPRECATED_StateCodeMapper extends ObjectCodeMapperBase {

  override def objectCodeMapping: Map[Int, GeneratedMessageCompanion[_]] = Map(
    1 -> ResourceUsageState,
    2 -> ItemManagerState
    //DON'T add any new entry here, this is deprecated
    //all new entries should be added to 'DefaultObjectCodeMapper'
  )
}
