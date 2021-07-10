package com.evernym.verity.actor.msgoutbox.latest

import com.evernym.verity.actor.typed.BehaviourSpecBase
import com.evernym.verity.testkit.BasicSpec

class OutboxRouterSpec
  extends BehaviourSpecBase
    with BasicSpec {

  "OutboxRouter" - {

    "when received a request to send a message" - {
      "should process it successfully" in {
        //this will test that behaviour is able to fetch relationship data
        // extracts/calculates target outbox ids
        pending
      }
    }

    "post routing" - {

      "when checked the message behaviour" - {
        "should find the message" in {
          pending
        }
      }

      "when checked the external storage" - {
        "should find the payload" in {
          pending
        }
      }

      "when checked the outbox behaviour" - {
        "should find message id" in {
          pending
        }
      }
    }
  }
}
