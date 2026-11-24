package fr.vlegall.seal.conversations.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Activity;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.ConversationSummary;
import fr.vlegall.seal.conversations.domain.model.ConversationType;
import fr.vlegall.seal.conversations.domain.model.GroupName;
import fr.vlegall.seal.conversations.domain.model.MemberProfile;
import fr.vlegall.seal.conversations.domain.model.MemberSummary;
import fr.vlegall.seal.conversations.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CNV-04, ADR 0039 (point 6) : vue JSON d'une conversation")
class ConversationViewTest {

    private static final Instant AT = Instant.parse("2026-11-24T10:00:00Z");

    private static ConversationSummary summary(Activity activity) {
        UserId me = new UserId(UUID.randomUUID());
        return new ConversationSummary(
                new ConversationId(UUID.randomUUID()),
                ConversationType.GROUP,
                Optional.of(GroupName.parse("Ski")),
                List.of(new MemberSummary(new MemberProfile(me, "moi", "Moi"), Role.ADMIN)),
                AT,
                12,
                activity);
    }

    @Test
    @DisplayName("CNV-04 : non-lus et dernier senderSeq repris de l'activité ; lastEnvelope null avant le lot 4")
    void cnv04ActivityIsCarriedToTheView() {
        ConversationView view = ConversationView.of(summary(new Activity(3, OptionalLong.of(7))));

        assertThat(view.unreadCount()).isEqualTo(3);
        assertThat(view.lastSenderSeq()).isEqualTo(7L);
        assertThat(view.lastServerSeq()).isEqualTo(12);
        assertThat(view.lastEnvelope()).isNull();
    }

    @Test
    @DisplayName("CNV-04 : sans activité, 0 non-lu et lastSenderSeq null")
    void cnv04NoActivityGivesZeroAndNull() {
        ConversationView view = ConversationView.of(summary(Activity.NONE));

        assertThat(view.unreadCount()).isZero();
        assertThat(view.lastSenderSeq()).isNull();
    }
}
