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
        class="event-conflicted ml-2"
        absolute
        icon
        fab>
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
      conflictWithOtherEvent: false,
    };
  },
  computed: {
    isCurrentUser() {
      return this.voter && this.voter.isCurrentUser;
    },
  },
  mounted() {
    this.computedEventConflicts();
  },
  methods: {
    computedEventConflicts() {
      if (this.voter && this.voter.isCurrentUser) {
        this.$eventService.getEvents(null, null, eXo.env.portal.userIdentityId, this.dateOption.start, this.dateOption.end, 1, ['ACCEPTED'])
          .then(data => this.conflictWithOtherEvent = data && data.events && data.events.length);
      }
    },
  },
};
</script>