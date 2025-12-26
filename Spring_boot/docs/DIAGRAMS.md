# Diagrams Index
## PNG Exports

- [Architecture](diagrams/png/architecture.png)
- [Data Flow: Real-Time](diagrams/png/data_flow_realtime.png)
- [WS Message Types](diagrams/png/ws_message_types.png)
- [Sequence: Bot Audio → STT → LLM](diagrams/png/sequence_bot_audio.png)
- [Sequence: Client Create Idea](diagrams/png/sequence_client_create_idea.png)
- [Sequence: React and Promotion](diagrams/png/sequence_react_promotion.png)
- [State: Idea Lifecycle](diagrams/png/state_idea_lifecycle.png)
- [Class Relationships](diagrams/png/class_relations.png)
- [Redis Key Schema](diagrams/png/redis_schema.png)
- [REST Endpoints](diagrams/png/rest_endpoints.png)
- [Kafka Bot Provisioning](diagrams/png/kafka_bot_provisioning.png)
- [STT Reconnect & Backpressure](diagrams/png/stt_reconnect.png)
- [Session Management](diagrams/png/session_management.png)
- [LLM Request/Response](diagrams/png/llm_request_response.png)

A collection of Mermaid diagrams capturing key flows and structures in the backend.

## System Architecture

```mermaid
graph LR
  subgraph Client
    UI[Web UI]
  end

  subgraph Backend[Spring Boot Backend]
    CWS[ClientWebSocketHandler]
    BWS[BotWebSocketHandler]
    SESS[SessionService]
    CREG[ClientRegistryService]
    ADUMP[AudioDumpService]

    STTR[SttRoutingService]
    STTWS[SttWebSocketClient]

    STTH[SttResponseHandler]
    IDEA_SVC[IdeaService]
    IEX[IdeaExtractorClient]
    LRF[LlmRequestFactoryService]
    LLM[GigaChatLlmClient]

    CNOTIF[ClientNotificationService]
    REDIS_IDEA[(RedisIdeaRepository)]
    REDIS_EVENT[(RedisEventRepository)]
    REDIS_CLIENT[(RedisClientRepository)]

    EC[EventController]
    ES[EventService]

    BOT_SVC[BotService]
    KAFKA_SVC[BotKafkaService]
  end

  UI -->|WS /ws/client| CWS
  UI -->|REST /events| EC

  CWS --> SESS
  CWS --> CREG
  BWS --> SESS

  BWS --> STTR --> STTWS
  STTWS --> EXT_STT[(External STT)]

  EXT_STT --> STTH
  STTH --> IDEA_SVC
  IDEA_SVC --> IEX --> LRF --> LLM
  IDEA_SVC --> REDIS_IDEA
  IDEA_SVC --> CNOTIF --> UI

  EC --> ES --> REDIS_EVENT
  ES --> REDIS_CLIENT

  UI -->|WS connect_bot| CWS --> BOT_SVC --> KAFKA_SVC --> BROKER[(Kafka broker)]
```

## Sequence: Bot Audio → STT → LLM → Ideas Broadcast

```mermaid
sequenceDiagram
  participant Bot
  participant BWS as BotWebSocketHandler
  participant STTR as SttRoutingService
  participant STTWS as SttWebSocketClient
  participant STT as External STT
  participant STTH as SttResponseHandler
  participant CREG as ClientRegistryService
  participant IDEA as IdeaService
  participant IEX as IdeaExtractorClient
  participant LRF as LlmRequestFactoryService
  participant LLM as GigaChatLlmClient
  participant REDIS as RedisIdeaRepository
  participant NOTIF as ClientNotificationService
  participant Clients

  Bot->>BWS: PCM16LE frames (binary)
  BWS->>STTR: forwardAudio(conferenceId,eventId,bytes)
  STTR->>STTWS: sendToStt({ type: audio, ... })
  STTWS->>STT: WebSocket send
  STT-->>STTWS: final_text JSON
  STTWS-->>STTH: payload
  STTH->>CREG: getClientInfo(clientId)
  STTH->>IDEA: processText(confId, confName, eventId, text)
  IDEA->>IEX: extractIdeas(text)
  IEX->>LRF: createIdeaExtractionRequest(text)
  LRF-->>IEX: request
  IEX->>LLM: sendRequest(request)
  LLM-->>IEX: response (ideas JSON)
  IEX-->>IDEA: ideas
  IDEA->>REDIS: saveIdea(idea)
  IDEA->>NOTIF: broadcastIdeaToConference(...)
  NOTIF-->>Clients: WS message { type: "idea" }
```

## Sequence: Client Create Idea

```mermaid
sequenceDiagram
  participant Client
  participant CWS as ClientWebSocketHandler
  participant SESS as SessionService
  participant IDEA as IdeaService
  participant REDIS as RedisIdeaRepository
  participant NOTIF as ClientNotificationService

  Client->>CWS: WS { type: create_idea, data }
  CWS->>SESS: getSessionData(session)
  CWS->>IDEA: createIdeaFromFront(confId, confName, eventId, title, desc)
  IDEA->>REDIS: saveIdea(idea)
  IDEA->>NOTIF: broadcastIdeaToConference(...)
  NOTIF-->>Client: WS { type: "idea" }
```

## Sequence: React and Promotion

```mermaid
sequenceDiagram
  participant Client
  participant CWS as ClientWebSocketHandler
  participant SESS as SessionService
  participant IDEA as IdeaService
  participant REDIS as RedisIdeaRepository
  participant NOTIF as ClientNotificationService

  Client->>CWS: WS { type: react_to_idea, ideaId, reaction }
  CWS->>SESS: getSessionData(session)
  CWS->>IDEA: reactToIdea(confId, eventId, ideaId, reaction, clientId)
  IDEA->>REDIS: saveIdea(idea)
  IDEA->>NOTIF: broadcastIdeaReaction(confId, ideaId, likes, dislikes)
  alt likes > participants/2 and not promoted
    IDEA->>REDIS: promote to GLOBAL
    IDEA->>NOTIF: broadcastIdeaStatusChanged(eventId, confId, ideaId, "global")
  end
```

## State: Idea Lifecycle

```mermaid
stateDiagram-v2
  [*] --> LOCAL
  LOCAL --> GLOBAL: Majority likes (>50% of participants)
  GLOBAL --> GOLDEN: Accept reaction
  LOCAL --> GOLDEN: Accept reaction
  note right of LOCAL: Pending ideas set
  note right of GLOBAL: Promoted, event-wide broadcasts
  note right of GOLDEN: Accepted/confirmed
```

## Class Relationships

```mermaid
classDiagram
  class IdeaService {
    +processText(...)
    +createIdeaFromFront(...)
    +reactToIdea(...)
    +getIdeasForEvent(eventId)
  }
  class IdeaExtractorClient {
    +extractIdeas(text)
  }
  class LlmRequestFactoryService
  class GigaChatLlmClient
  class ClientNotificationService
  class RedisIdeaRepository
  class SessionService
  class SttResponseHandler
  class SttRoutingService
  class SttWebSocketClient
  class ClientRegistryService
  class BotService
  class BotKafkaService

  IdeaService --> IdeaExtractorClient
  IdeaService --> ClientNotificationService
  IdeaService --> RedisIdeaRepository
  IdeaService --> SessionService
  SttResponseHandler --> IdeaService
  SttResponseHandler --> ClientRegistryService
  SttRoutingService --> SttWebSocketClient
  BotService --> SessionService
  BotService --> BotKafkaService
  BotService --> ClientNotificationService
```

## Redis Key Schema Overview

```mermaid
flowchart TD
  subgraph Redis
    I[idea:{ideaId} -> Idea]
    P[event:{eventId}:ideas:pending -> Set(ideaId)]
    A[event:{eventId}:ideas:accepted -> Set(ideaId)]
    R[event:{eventId}:ideas:rejected -> Set(ideaId)]
    E[event:{eventId} -> Event]
    EP[event:{eventId}:participants -> Set(conferenceId)]
    CNF[conference:{conferenceId}:name -> String]
    CCM[client:{clientId} -> { conferenceId, eventId }]
  end

  I --- P
  I --- A
  I --- R
  E --- EP
  CNF --> E
  CCM --> CNF
```

## WebSocket Message Types

```mermaid
flowchart LR
  ClientMsgs[[Client inbound]] -->|WS| CWS
  CWS -->|type: vote| VoteService
  CWS -->|type: create_idea| IdeaService
  CWS -->|type: delete_idea| IdeaService
  CWS -->|type: react_to_idea| IdeaService
  CWS -->|type: connect_bot| BotService
  CWS -->|type: disconnect_bot| BotService

  STTMsgs[[STT inbound]] --> STTH
  STTH -->|type: final_text| IdeaService

  Outbound --> CNOTIF
  CNOTIF -->|idea| Clients
  CNOTIF -->|idea_deleted| Clients
  CNOTIF -->|idea_reaction| Clients
  CNOTIF -->|idea_status_changed| Clients
  CNOTIF -->|ideas_list| Clients
  CNOTIF -->|participants_count| Clients
  CNOTIF -->|bot_connected| Clients
  CNOTIF -->|bot_disconnected| Clients
```

## REST Endpoints Map

```mermaid
flowchart TD
  Client -->|POST /events/create| EC[EventController]
  EC --> ES[EventService]
  ES --> REDIS_EVENT
  ES --> REDIS_CLIENT

  Client -->|POST /events/{eventId}/join| EC
  EC --> ES
  ES --> REDIS_EVENT
  ES --> REDIS_CLIENT

  Client -->|POST /events/{eventId}/end| EC
  EC --> ES
  ES --> REDIS_EVENT

  Client -->|GET /events/{eventId}/summary| EC
  EC --> ES
```

## Kafka Bot Provisioning

```mermaid
flowchart LR
  Client -- WS: connect_bot --> CWS
  CWS --> BOT_SVC[BotService]
  BOT_SVC --> KAFKA_SVC[BotKafkaService]
  KAFKA_SVC -->|produce| Broker[(Kafka broker)]
```

## STT Reconnection & Backpressure

```mermaid
flowchart TD
  STTWS[SttWebSocketClient]
  STTWS -->|outQueue (Sinks.Many)| Sender
  STTWS -->|ReactorNetty client.execute| Connection
  Connection -->|retryWhen(backoff)| Reconnect
  Reconnect --> STTWS
  Connection -->|on receive| STTH[SttResponseHandler]
  Note[Backoff resets on successful connect]
```

## Session Management (connect/disconnect)

```mermaid
sequenceDiagram
  participant Client
  participant CWS as ClientWebSocketHandler
  participant REDIS as RedisClientRepository / RedisEventRepository
  participant CREG as ClientRegistryService
  participant SESS as SessionService
  participant NOTIF as ClientNotificationService

  Client->>CWS: WS connect with clientId,eventId
  CWS->>REDIS: fetch conferenceId, conferenceName
  CWS->>CREG: registerClient(clientId, conferenceId, eventId, name)
  CWS->>SESS: registerClient(session, clientId, ...)
  CWS->>NOTIF: broadcastParticipantsCount(conferenceId, count)
  ...
  Client--xCWS: WS disconnect
  CWS->>CREG: unregisterClient(clientId)
  CWS->>SESS: unregisterClient(session)
  CWS->>NOTIF: broadcastParticipantsCount(conferenceId, count)
```

## LLM Request/Response

```mermaid
sequenceDiagram
  participant IDEA as IdeaExtractorClient
  participant LRF as LlmRequestFactoryService
  participant LLM as GigaChatLlmClient
  participant DTO as GigaChatRequestDto/GigaChatResponseDto

  IDEA->>LRF: createIdeaExtractionRequest(text)
  LRF-->>IDEA: LlmRequest
  IDEA->>LLM: sendRequest(LlmRequest)
  LLM->>DTO: build request + WebClient.post()
  DTO-->>LLM: response JSON
  LLM-->>IDEA: LlmResponse (content/model/usage)
  IDEA-->>IDEA: parse ideas from JSON
```

---

If you want PNG/SVG exports or additional deep-dive sequence diagrams (e.g., voting edge cases, STT error handling), let me know which to prioritize.
