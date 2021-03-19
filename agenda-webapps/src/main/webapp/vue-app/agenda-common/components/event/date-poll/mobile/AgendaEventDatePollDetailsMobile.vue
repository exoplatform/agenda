<template>
  <v-flex class="agenda-date-poll-details-mobile">
    <div class="ml-3 mt-5 mb-3 grey--text text-subtitle-2">
      {{ $t('agenda.votePreferredDates') }}
    </div>
    <agenda-event-date-poll-item-mobile
      :event="event"
      :voted-date-polls="votedDatePolls"
      :date-options="dateOptions"
      :voters="voters"
      :current-user-votes="currentUserVotes"
      :is-voting="isVoting"
      :can-select-date="canSelectDate"
      @selectDate="$emit('selectDate', $event)"
      @votes-changed="$emit('enableVoteButton')" />
    <agenda-date-poll-participants-drawer-mobile :event-title="event.summary" />
  </v-flex>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
    votedDatePolls: {
      type: Array,
      default: () => null
    },
    dateOptions: {
      type: Array,
      default: () => null
    },
    voters: {
      type: Array,
      default: () => null
    },
    currentUserVotes: {
      type: Object,
      default: () => null
    },
    canSelectDate: {
      type: Boolean,
      default: false,
    },
    isVoting: {
      type: Boolean,
      default: false,
    },
  },
};
</script>