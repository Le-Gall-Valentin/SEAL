package fr.vlegall.seal.conversations.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Activity;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.port.out.ConversationActivityPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CNV-04, ADR 0039 (point 6) : activité de chaque conversation dans la vue")
class ConversationSummariesTest {

    private final InMemoryConversations store = new InMemoryConversations();
    private final CreateGroupHandler groups =
            new CreateGroupHandler(store.repository(), store.activeFriends(), store.summaries(), store.clock);
    private final UserId me = store.person("moi");
    private final UserId friend = store.person("ami");

    ConversationSummariesTest() {
        store.befriend(me, friend);
    }

    @Test
    @DisplayName("CNV-04 : non-lus et dernier senderSeq du port arrivent dans la vue ; sans activité, 0 et rien")
    void cnv04ActivityOfThePortReachesTheView() {
        ConversationId busy = groups.create(me, "Ski", Set.of(friend)).id();
        ConversationId quiet = groups.create(me, "Raquette", Set.of(friend)).id();
        List<UserId> askedFor = new ArrayList<>();
        ConversationActivityPort activity = (member, conversations) -> {
            askedFor.add(member);
            return Map.of(busy, new Activity(3, OptionalLong.of(7)));
        };
        ConversationSummaries summaries = new ConversationSummaries(store.directory(), activity);

        List<ConversationSummary> listed = summaries.listed(me, List.of(store.byId.get(busy), store.byId.get(quiet)));

        assertThat(listed)
                .extracting(ConversationSummary::activity)
                .containsExactly(new Activity(3, OptionalLong.of(7)), Activity.NONE);
        assertThat(summaries.of(me, store.byId.get(busy)).activity()).isEqualTo(new Activity(3, OptionalLong.of(7)));
        assertThat(askedFor).containsOnly(me);
    }
}
