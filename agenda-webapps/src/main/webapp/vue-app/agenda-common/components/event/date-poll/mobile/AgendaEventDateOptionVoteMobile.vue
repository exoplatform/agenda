<template>
  <div class="event-date-poll-vote-choice">
    <v-checkbox
      v-model="vote"
      :disabled="disabled || !isCurrentUser"
      class="event-checkbox-vote ma-auto pa-0"
      hide-details
      @change="$emit('change', vote)" />
    <v-fab-transition>
      <v-btn
        v-show="conflictWithOtherEvent"
        :title="$t('agenda.conflictWithOtherEvent')"
        class="event-conflicted"
        absolute
        icon
        fab
        @click="$root.$emit('agenda-conflict-events-drawer-open', dateOption, conflictEvents)">
        <v-icon size="20px" color="#f8b441">warning</v-icon>
      </v-btn>
    </v-fab-transition>
  </div>
</template>

<script>
export default {
  props: {
    voter: {
      type: Object,
      default: () => null
    },
    dateOption: {
      type: Object,
      default: () => null
    },
    vote: {
      type: Boolean,
      default: false,
    },
    disabled: {
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
    conflictWithOtherEvent() {
      return this.conflictEvents && this.conflictEvents.length;
    },
  },
  mounted() {
    this.computeEventConflicts();
  },
  methods: {
    computeEventConflicts() {
      if (this.voter && this.voter.isCurrentUser) {
        const start = this.$agendaUtils.toRFC3339(this.dateOption.start, this.dateOption.allDay, true);
        const end = this.$agendaUtils.toRFC3339(this.dateOption.end, this.dateOption.allDay, true).replace('00:00:00', '23:59:59');

        this.$eventService.getEvents(null, null, eXo.env.portal.userIdentityId, start, end, 10, ['ACCEPTED','TENTATIVE'])
          .then(data => this.conflictEvents = data && data.events);
      }
    },
  },
};
</script>