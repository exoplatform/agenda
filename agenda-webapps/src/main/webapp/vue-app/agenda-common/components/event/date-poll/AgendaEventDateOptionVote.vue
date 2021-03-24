<template>
  <div class="event-date-poll-vote-choice d-flex flex-row flex-nowrap">
    <div class="my-auto d-inline">
      <v-checkbox
        v-if="isCurrentUserVoting"
        v-model="vote"
        :disabled="disabled || !isCurrentUser"
        class="event-checkbox-vote pa-0"
        hide-details
        @change="$emit('change', vote)" />
      <template v-else>
        <span v-if="!hasVoted"></span>
        <i v-else-if="vote" class="uiIcon py-1 uiIconColorValidate"></i>
        <i v-else class="uiIcon py-1 uiIconColorError"></i>
      </template>
    </div>
    <v-fab-transition>
      <v-btn
        v-show="conflictWithOtherEvent"
        :title="$t('agenda.conflictWithOtherEvent')"
        :absolute="!isMobile"
        :small="isMobile"
        :class="isMobile && 'ml-2 mb-3' || 'ml-8'"
        class="event-conflicted"
        icon
        @click="$root.$emit('agenda-conflict-events-drawer-open', dateOption, conflictEvents, conflictingDatePolls)">
        <v-icon size="20px" color="#f8b441">warning</v-icon>
      </v-btn>
    </v-fab-transition>
  </div>
</template>
<script>
export default {
  props: {
    votedDatePolls: {
      type: Array,
      default: () => null
    },
    voter: {
      type: Object,
      default: () => null
    },
    dateOption: {
      type: Object,
      default: () => null
    },
    isVoting: {
      type: Boolean,
      default: false,
    },
    vote: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      conflictEvents: null,
    };
  },
  computed: {
    isCurrentUser() {
      return this.voter && this.voter.isCurrentUser;
    },
    isCurrentUserVoting() {
      return this.isCurrentUser && this.isVoting;
    },
    hasVoted() {
      return this.voter && this.voter.hasVoted;
    },
    conflictWithOtherEvent() {
      return (this.conflictEvents && this.conflictEvents.length)
              || (this.conflictingDatePolls && this.conflictingDatePolls.length);
    },
    conflictingDatePolls() {
      if (!this.isCurrentUser) {
        return null;
      }
      const conflictingDatePolls = [];
      const eventStart = new Date(this.dateOption.start);
      const eventEnd = new Date(this.dateOption.end);
      if (this.votedDatePolls && this.votedDatePolls.length) {
        this.votedDatePolls.forEach(datePoll => {
          const datePollStart = new Date(datePoll.start);
          const datePollEnd = new Date(datePoll.end);
          if (datePollStart.getTime() < eventEnd.getTime()
              && datePollEnd.getTime() > eventStart.getTime()) {
            conflictingDatePolls.push(datePoll);
          }
        });
      }
      return conflictingDatePolls;
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
  },
  mounted() {
    this.computeEventConflicts();
  },
  methods: {
    computeEventConflicts() {
      if (this.isCurrentUser) {
        const start = this.$agendaUtils.toRFC3339(this.dateOption.start, this.dateOption.allDay, true);
        const end = this.$agendaUtils.toRFC3339(this.dateOption.end, this.dateOption.allDay, true).replace('00:00:00', '23:59:59');

        this.$eventService.getEvents(null, null, eXo.env.portal.userIdentityId, start, end, 10, ['ACCEPTED','TENTATIVE'])
          .then(data => this.conflictEvents = data && data.events);
      }
    },
  },
};
</script>