<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <div>
    <template v-if="isVoting">
      <v-btn
        :disabled="sendingVotes"
        class="btn ms-auto me-2"
        @click="$root.$emit('agenda-date-poll-canceled')">
        {{ $t('agenda.button.cancel') }}
      </v-btn>
      <v-btn
        v-if="currentUserVotes"
        :loading="sendingVotes"
        :disabled="sendingVotes || disableVoteButton"
        class="btn btn-primary"
        @click="sendVotes">
        {{ $t('agenda.button.vote') }}
      </v-btn>
    </template>
    <v-btn
      v-else-if="isCreator"
      :loading="creatingEvent"
      :disabled="disableCreateButton"
      class="btn btn-primary ms-auto"
      @click="createEvent">
      {{ $t('agenda.button.createEvent') }}
    </v-btn>
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
    currentUserVotes: {
      type: Object,
      default: () => null
    },
    isVoting: {
      type: Boolean,
      default: false
    },
    sendingVotes: {
      type: Boolean,
      default: false
    },
    selectedDateIndex: {
      type: Number,
      default: -1
    },
    disableVoteButton: {
      type: Boolean,
      default: true
    },
    voters: {
      type: Array,
      default: () => null
    },
    dateOptions: {
      type: Array,
      default: () => null
    },
  },
  data: () => ({
    creatingEvent: false,
    currentUserId: Number(eXo.env.portal.userIdentityId),
  }),
  computed: {
    isCreator() {
      return this.event && this.event.creator && Number(this.event.creator.id) === this.currentUserId;
    },
    disableCreateButton() {
      return this.selectedDateIndex < 0 || this.creatingEvent;
    },
  },
  created() {
    this.$root.$on('agenda-date-poll-voted', () => {
      this.isVoting = false;
      this.sendingVotes = false;
    });
  },
  methods: {
    createEvent() {
      const dateOption = this.dateOptions[this.selectedDateIndex];
      this.creatingEvent = true;
      return this.$eventService.selectEventDate(dateOption.eventId, dateOption.id)
        .then(() => {
          this.$root.$emit('agenda-refresh');
          window.setTimeout(() => this.$root.$emit('agenda-event-details', this.event), 200);
        })
        .finally(() => {
          window.setTimeout(() => {
            this.creatingEvent = false;
          }, 200);
        });
    },
    sendVotes() {
      const eventId = this.event.id;
      const acceptedDateOptionIds = [];
      for (const index in this.dateOptions) {
        const dateOptionId = this.dateOptions[index].id;
        if (this.currentUserVotes.dateOptionVotes[index]) {
          acceptedDateOptionIds.push(dateOptionId);
        }
      }
      this.sendingVotes = true;
      this.$eventService.saveEventVotes(eventId, acceptedDateOptionIds)
        .then(() => {
          this.$emit('refresh-event');
          window.setTimeout(() => {
            this.$root.$emit('agenda-date-poll-voted');
          }, 500);
        });
    },
  },
};
</script>