<template>
  <div
    v-if="visible"
    :class="isCounter && 'd-flex flex-row align-center' || 'd-flex flex-column mb-2'"
    class="event-form-busy-coverage">
    <!-- THE COUNTER. Compact by design, and always present while there are
         participants: an empty grid can only be READ as "this slot is free"
         if the screen says whose calendars it is showing. The SENTENCE moves
         into the tooltip; the NUMBER does not, because a number is scannable
         and an icon alone is not. -->
    <div
      v-if="isCounter"
      :title="coverageSentence"
      :aria-label="coverageSentence"
      class="d-flex flex-row align-center">
      <v-icon size="18" class="me-1 icon-default-color">fas fa-user-clock</v-icon>
      <span class="caption">{{ checkedCount }}/{{ participantCount }}</span>
    </div>
    <!-- THE REPORT. Body text, never a tooltip, and only when there is
         something to own up to.

         Two reasons it is not a tooltip, and they are the point of the whole
         feature. A tooltip does not exist on touch, so on mobile these lines
         would be unreachable. And an organiser who never hovers never learns
         their grid is incomplete — which is exactly the "a state that looks
         like an answer" failure this screen was built to prevent. Hiding it
         behind a hover would trade away the property EXO-89842, EXO-89843 and
         EXO-89850 exist to establish.

         Two lines rather than one merged list, for the reason they always
         were: not sharing is a colleague's decision and will not change by
         reloading, while a failed read is a breakage that may be gone in a
         minute, and an organiser does different things about them. -->
    <template v-else>
      <div
        v-if="notDisclosedNames.length"
        class="d-flex flex-row align-start caption">
        <v-icon size="14" class="me-1 mt-1 icon-default-color">fas fa-eye-slash</v-icon>
        <span>{{ notDisclosedSentence }}</span>
      </div>
      <div
        v-if="failedNames.length"
        class="d-flex flex-row align-start caption warning--text mt-1">
        <v-icon
          size="14"
          color="warning"
          class="me-1 mt-1">
          fas fa-exclamation-triangle
        </v-icon>
        <span>{{ failedSentence }}</span>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  props: {
    /*
     * Which half of the coverage this instance renders: the compact counter
     * that sits in the header beside the connected-account plug, or the
     * report of who could not be checked, which goes above the grid as body
     * text. One component owns both so that the counting and the naming
     * cannot drift apart into two answers.
     */
    variant: {
      type: String,
      default: 'counter',
    },
    /*
     * The participants this screen undertook to check: the event's user
     * attendees other than the organiser, in the order the attendee list
     * holds them.
     */
    participants: {
      type: Array,
      default: () => [],
    },
    /*
     * Keys of the participants whose calendar was actually read. Those are the
     * only ones whose absence from the grid means they are free.
     *
     * Participant KEYS (providerId:remoteId), never identity ids: on an event
     * that has not been saved yet a participant has no identity id, and one
     * whose identity could not be resolved at all never gets one — and that
     * participant is exactly the one the strip must still be able to name.
     */
    checkedKeys: {
      type: Array,
      default: () => [],
    },
    /*
     * Keys of the participants who do not disclose their busy time to the
     * current user.
     */
    notDisclosedKeys: {
      type: Array,
      default: () => [],
    },
    /*
     * Keys of the participants nothing could be read about: the read broke,
     * or the platform could not resolve who they are. Both are breakages
     * rather than choices, and neither is an answer.
     */
    failedKeys: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    /**
     * Whether this instance is the header counter rather than the report.
     *
     * @returns {Boolean} true for the compact counter
     */
    isCounter() {
      return this.variant === 'counter';
    },
    /**
     * Whether this instance renders anything at all.
     *
     * <p>
     * The counter shows whenever there is a participant to count, because the
     * grid can only be read as "free" if something says whose calendars it
     * covers. The report shows only when somebody could not be checked — with
     * everybody checked it draws no empty container and no margin, and the
     * grid gets that space back.
     *
     * @returns {Boolean} true when this instance has something to say
     */
    visible() {
      if (!this.participantCount) {
        return false;
      }
      return this.isCounter || this.notDisclosedNames.length > 0 || this.failedNames.length > 0;
    },
    /**
     * How many people this screen set out to check.
     *
     * @returns {Number} the participant count
     */
    participantCount() {
      return this.participants.length;
    },
    /**
     * How many of them a calendar was actually read for.
     *
     * <p>
     * Counted against the participant list rather than off the length of
     * `checkedIds`, so that a stale answer about somebody who has since been
     * removed from the event cannot inflate the coverage the sentence claims.
     *
     * @returns {Number} the number of participants whose busy time is on the
     *          grid
     */
    checkedCount() {
      return this.participants.filter(participant => this.checkedKeys.includes(this.keyOf(participant))).length;
    },
    /**
     * The names of the participants who share nothing with the current user.
     *
     * @returns {Array} their display names, in attendee order
     */
    notDisclosedNames() {
      return this.namesOf(this.notDisclosedKeys);
    },
    /**
     * The names of the participants whose busy time could not be read.
     *
     * @returns {Array} their display names, in attendee order
     */
    failedNames() {
      return this.namesOf(this.failedKeys);
    },
    /**
     * The always-present statement of what the grid covers.
     *
     * @returns {String} the coverage sentence
     */
    coverageSentence() {
      return this.$t('agenda.eventForm.busyTimeCoverage', {
        0: this.checkedCount,
        1: this.participantCount,
      });
    },
    /**
     * What the screen says about the people who share nothing. It states the
     * consequence for the choice being made — this grid says nothing about
     * them — rather than merely reporting a setting.
     *
     * @returns {String} the sentence
     */
    notDisclosedSentence() {
      return this.$t('agenda.eventForm.busyTimeNotShared', {
        0: this.notDisclosedNames.join(', '),
      });
    },
    /**
     * What the screen says about the people whose read broke.
     *
     * @returns {String} the sentence
     */
    failedSentence() {
      return this.$t('agenda.eventForm.busyTimeNotChecked', {
        0: this.failedNames.join(', '),
      });
    },
  },
  methods: {
    /**
     * How a participant is keyed, the one way this whole screen keys them.
     *
     * @param {Object} participant an event attendee
     * @returns {String} their participant key, empty when unknown
     */
    keyOf(participant) {
      return this.$agendaUtils.participantKey(participant);
    },
    /**
     * Names the participants carrying the given keys.
     *
     * <p>
     * Driven off the participant list rather than off the key list, so that
     * the order matches the attendee list the organiser is looking at, and so
     * that a key the screen no longer has a participant for cannot produce a
     * nameless entry.
     *
     * @param {Array} keys the participant keys to name
     * @returns {Array} the display names, in attendee order
     */
    namesOf(keys) {
      return this.participants
        .filter(participant => keys.includes(this.keyOf(participant)))
        .map(participant => this.displayNameOf(participant))
        .filter(name => !!name);
    },
    /**
     * How a participant is named to the organiser.
     *
     * @param {Object} participant an event attendee
     * @returns {String} their full name, falling back to their username
     */
    displayNameOf(participant) {
      const profile = participant && participant.identity && participant.identity.profile;
      return profile && (profile.fullname || profile.fullName)
        || participant && participant.identity && participant.identity.remoteId
        || '';
    },
  },
};
</script>
