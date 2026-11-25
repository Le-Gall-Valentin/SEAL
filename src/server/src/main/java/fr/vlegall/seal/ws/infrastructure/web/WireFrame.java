package fr.vlegall.seal.ws.infrastructure.web;

import tools.jackson.databind.JsonNode;

/** TRN-14 : une trame lue, { type, id, payload }. */
public record WireFrame(String type, String id, JsonNode payload) {}
