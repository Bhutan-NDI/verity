package com.evernym.verity.actor.event.serializer

import com.evernym.verity.actor._
import com.evernym.verity.actor.agent.msgrouter.{RouteProcessed, StatusUpdated}
import com.evernym.verity.actor.cluster_singleton.fixlegacyroutes.{Completed, Registered}
import com.evernym.verity.protocol.protocols.agentprovisioning.{v_0_5 => ap5, v_0_6 => ap6, v_0_7 => ap7}
import com.evernym.verity.protocol.protocols.committedAnswer.{v_1_0 => committedAnswer_v10}
import com.evernym.verity.protocol.protocols.connections.{v_1_0 => connections_10}
import com.evernym.verity.protocol.protocols.issueCredential.{v_1_0 => issueCredential_v10}
import com.evernym.verity.protocol.protocols.issuersetup.{v_0_6 => issuerSetup_v06}
import com.evernym.verity.protocol.protocols.outofband.{v_1_0 => outOfBand_v10}
import com.evernym.verity.protocol.protocols.presentproof.{v_1_0 => presentProof_v10}
import com.evernym.verity.protocol.protocols.questionAnswer.{v_1_0 => questionAnswer_v10}
import com.evernym.verity.protocol.protocols.relationship.{v_1_0 => relationship_10}
import com.evernym.verity.protocol.protocols.trustping.{v_1_0 => trustping_v10}
import com.evernym.verity.protocol.protocols.writeCredentialDefinition.{v_0_6 => writeCredDef_v06}
import com.evernym.verity.protocol.protocols.writeSchema.{v_0_6 => writeSchema_v06}
import com.evernym.verity.protocol.protocols.{deaddrop, walletBackup, tictactoe => tictactoe_v0_5, tokenizer => tk}
import com.evernym.verity.protocol.{SetDomainId, SetPinstId}
import com.evernym.verity.transformer.{EventDataTransformer, TransformedData}
import com.evernym.verity.urlmapper.UrlAdded
import com.google.protobuf.ByteString
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}


trait EventSerializer {

  val eventMapper: EventCodeMapper

  def getTransformedEvent(evt: Any, encryptionKey: String): TransformedEvent = {
    val sebd = evt.asInstanceOf[GeneratedMessage].toByteArray
    val ed = EventDataTransformer.pack(sebd, encryptionKey)
    val data = ByteString.copyFrom(ed.data)
    TransformedEvent(ed.transformationId, eventMapper.getCodeFromClass(evt), data)
  }

  def getDeSerializedEvent(etd: TransformedEvent, encryptionKey: String): Any = {
    val bed = etd.data.toByteArray
    val dd = EventDataTransformer.unpack(TransformedData(bed, etd.transformationId), encryptionKey)
    val sebd = dd.asInstanceOf[Array[Byte]]
    eventMapper.getClassFromCode(etd.eventCode, sebd)
  }
}

object DefaultEventSerializer extends EventSerializer {
  val eventMapper: EventCodeMapper = DefaultEventMapper
}

object DefaultEventMapper extends EventCodeMapper {

  //NOTE: Never change the key (numbers) in below map once it is assigned and in use

  lazy val eventCodeMapping: Map[Int, GeneratedMessageCompanion[_]] = Map (
    1  -> KeyCreated,
    2  -> SignedUp,
    3  -> OwnerSetForAgent,
    4  -> AgentKeyCreated,
    5  -> AgentDetailSet,
    6  -> AgentCreated,
    7  -> AgentKeyDlgProofSet,
    8  -> TheirAgentKeyDlgProofSet,
    9  -> TheirAgencyIdentitySet,
    10 -> TheirAgentDetailSet,
    11 -> MsgCreated,
    12 -> MsgPayloadStored,
    13 -> MsgDetailAdded,
    14 -> MsgAnswered,
    15 -> MsgStatusUpdated,
    16 -> MsgDeliveryStatusUpdated,
    17 -> EndpointSet,
    18 -> MappingAdded,
    19 -> RouteSet,
    20 -> UrlAdded,
    21 -> OwnerDIDSet,
    22 -> ConfigUpdated,
    23 -> ConfigRemoved,
    24 -> TokenToActorItemMappingAdded,
    25 -> ComMethodUpdated,
    26 -> ConnStatusUpdated,
    27 -> CallerBlocked,
    28 -> CallerResourceBlocked,
    29 -> CallerUnblocked,
    30 -> CallerResourceUnblocked,
    31 -> RouteMigrated,
    32 -> ComMethodDeleted,
    33 -> ResourceBucketUsageUpdated,
    34 -> ResourceUsageCounterUpdated,
    35 -> ResourceUsageLimitUpdated,
    36 -> CallerWarned,
    37 -> CallerResourceWarned,
    38 -> CallerUnwarned,
    39 -> CallerResourceUnwarned,
    40 -> ItemContainerConfigSet,
    41 -> ItemUpdated,
    42 -> MigrationStarted,
    43 -> ItemMigrated,
    44 -> MigrationFinished,
    45 -> MigrationCompletedByContainer,
    46 -> ItemContainerPrevIdUpdated,
    47 -> ItemContainerNextIdUpdated,
    48 -> ConnectionCompleted,
    49 -> MsgExpirationTimeUpdated,
    50 -> ProtocolInitialized,
    51 -> ProtocolObserverAdded,
    52 -> ConnectionStatusUpdated,
    53 -> ProtocolIdDetailSet,
    54 -> ThreadContextStored,
    55 -> SetPinstId, //NOTE: this is not used anymore, but we'll have to keep it for backward compatibility
    56 -> walletBackup.WalletBackupInitialized,
    57 -> walletBackup.ProvisionRequested,
    58 -> walletBackup.ReadyToExport,
    59 -> walletBackup.ReadyToPersist,
    60 -> walletBackup.BackupInProgress,
    61 -> walletBackup.BackupStored,
    62 -> walletBackup.BackupStoredAck,
    63 -> ap5.RequesterPartiSet,
    64 -> ap5.ProvisioningInitiaterPartiSet,
    65 -> ap5.PairwiseEndpointSet,
    66 -> ap5.PairwiseDIDSet,
    67 -> ap5.SignedUp,
    68 -> ap5.AgentPairwiseKeyCreated,
    69 -> ap5.UserAgentCreated,
    70 -> ap6.AgentPairwiseKeyCreated,
    71 -> ap6.RequesterPartiSet,
    72 -> ap6.ProvisionerPartiSet,
    73 -> ap6.UserAgentCreated,
    75 -> SegmentedStateStored,
    76 -> walletBackup.RequestedRecoveryKeySetup,
    77 -> walletBackup.RecoveredBackup,
    78 -> RequesterKeyAdded,
    79 -> deaddrop.Initialized,
    80 -> deaddrop.Item,
    81 -> deaddrop.RoleSet,
    82 -> writeSchema_v06.RequestReceived,
    83 -> writeSchema_v06.SchemaWritten,
    84 -> writeSchema_v06.WriteFailed,
    85 -> issuerSetup_v06.RosterInitialized,
    86 -> issuerSetup_v06.CreatePublicIdentifierInitiated,
    87 -> issuerSetup_v06.CreatePublicIdentifierCompleted,
    89 -> questionAnswer_v10.Initialized,
    90 -> questionAnswer_v10.MyRole,
    92 -> questionAnswer_v10.QuestionUsed,
    93 -> questionAnswer_v10.SignedAnswerUsed,
    94 -> questionAnswer_v10.AnswerUsed,
    95 -> questionAnswer_v10.Validity,
    96 -> writeCredDef_v06.RequestReceived,
    97 -> writeCredDef_v06.CredDefWritten,
    98 -> writeCredDef_v06.WriteFailed,
    99 -> tictactoe_v0_5.Initialized,
    100 -> tictactoe_v0_5.Offered,
    101 -> tictactoe_v0_5.Accepted,
    102 -> tictactoe_v0_5.Declined,
    103 -> tictactoe_v0_5.Forfeited,
    104 -> tictactoe_v0_5.Moved,
    105 -> tictactoe_v0_5.GameFinished,
    /* ** DO NOT REUSE NUMBERS **/
    //    106 -> issueCredential_v06.Initialized,
    //    107 -> issueCredential_v06.LearnedRole,
    //    108 -> issueCredential_v06.GotMsgFromDriver,
    //    109 -> issueCredential_v06.OfferSent,
    //    110 -> issueCredential_v06.OfferReceived,
    //    111 -> issueCredential_v06.RequestSent,
    //    112 -> issueCredential_v06.RequestReceived,
    //    113 -> issueCredential_v06.CredentialSent,
    //    114 -> issueCredential_v06.CredentialReceived,
    //    115 -> issueCredential_v06.ProtocolFailed,
    //    116 -> presentProof_v06.Initialized,
    //    117 -> presentProof_v06.LearnedRole,
    //    118 -> presentProof_v06.GotInitMsgFromDriver,
    //    119 -> presentProof_v06.ProofRequestSent,
    //    120 -> presentProof_v06.ProofRequestReceived,
    //    121 -> presentProof_v06.ProofSent,
    //    122 -> presentProof_v06.GotProof,
    //    123 -> presentProof_v06.ProtocolFailed,
    124 -> committedAnswer_v10.Initialized,
    125 -> committedAnswer_v10.MyRole,
    126 -> committedAnswer_v10.QuestionUsed,
    127 -> committedAnswer_v10.SignedAnswerUsed,
    128 -> committedAnswer_v10.Validity,
    129 -> committedAnswer_v10.Error,
    130 -> StorageReferenceStored,
    131 -> MigratedContainerStorageCleaned,
    132 -> ap7.AskedForProvisioning,
    133 -> ap7.AgentProvisioned,
    134 -> ap7.ProvisionFailed,
    135 -> ap7.RequestedAgentCreation,
    136 -> tk.RequestedToken,
    137 -> tk.CreatedToken,
    138 -> tk.ReceivedToken,
    139 -> tk.Failed,

    // below are aries interop protocol related
    140 -> connections_10.Initialized,
    141 -> connections_10.PreparedWithDID,
    142 -> connections_10.PreparedWithKey,
    143 -> connections_10.InvitedWithDID,
    144 -> connections_10.InvitedWithKey,
    145 -> connections_10.InviteAccepted,
    146 -> connections_10.RequestSent,
    147 -> connections_10.RequestReceived,
    148 -> connections_10.ResponseSent,
    149 -> connections_10.ResponseReceived,
    150 -> connections_10.AckSent,
    151 -> connections_10.AckReceived,

    152 -> FirstProtoMsgSent,

    153 -> relationship_10.Initialized,
    154 -> relationship_10.CreatingPairwiseKey,
    155 -> relationship_10.PairwiseKeyCreated,
    156 -> relationship_10.InvitationCreated,

    157 -> issueCredential_v10.Initialized,
    158 -> issueCredential_v10.ProposalSent,
    159 -> issueCredential_v10.OfferSent,
    160 -> issueCredential_v10.RequestSent,
    161 -> issueCredential_v10.IssueCredSent,
    162 -> issueCredential_v10.ProposalReceived,
    163 -> issueCredential_v10.OfferReceived,
    164 -> issueCredential_v10.RequestReceived,
    165 -> issueCredential_v10.IssueCredReceived,
    166 -> issueCredential_v10.Rejected,
    167 -> issueCredential_v10.ProblemReportReceived,

    168 -> presentProof_v10.Init,
    169 -> presentProof_v10.MyRole,
    170 -> presentProof_v10.Participants,
    171 -> presentProof_v10.RequestGiven,
    172 -> presentProof_v10.RequestUsed,
    173 -> presentProof_v10.PresentationUsed,
    174 -> presentProof_v10.PresentationGiven,
    175 -> presentProof_v10.PresentationAck,
    176 -> presentProof_v10.AttributesGiven,
    177 -> presentProof_v10.ResultsOfVerification,
    178 -> presentProof_v10.Rejection,

    179 -> PublicIdentityStored,

    180 -> SponsorAssigned,
    181 -> ap7.RequestedEdgeAgentCreation,
    182 -> writeSchema_v06.AskedForEndorsement,
    183 -> writeCredDef_v06.AskedForEndorsement,

    184 -> SetDomainId,

    185 -> trustping_v10.Initialized,
    186 -> trustping_v10.MyRole,
    187 -> trustping_v10.SentPing,
    188 -> trustping_v10.ReceivedPing,
    189 -> trustping_v10.SentResponse,
    190 -> trustping_v10.ReceivedResponse,

    191 -> ProtoMsgSenderOrderIncremented,
    192 -> ProtoMsgReceivedOrderIncremented,

    193 -> outOfBand_v10.Initialized,
    194 -> outOfBand_v10.ConnectionReuseRequested,
    195 -> outOfBand_v10.ConnectionReused,

    196 -> RecoveryKeyAdded,

    197 -> Registered,
    198 -> Completed,
    199 -> StatusUpdated,
    200 -> RouteProcessed,
    201 -> RecordingAgentActivity,
    202 -> WindowActivityDefined
  )

}
