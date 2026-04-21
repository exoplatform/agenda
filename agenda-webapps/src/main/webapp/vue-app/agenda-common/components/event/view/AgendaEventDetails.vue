<template>
  <v-card
    v-if="event"
    flat
    class="event-details d-flex flex-column">
    <agenda-event-details-mobile-toolbar
      v-if="$root.isMobile && !isDatePoll"
      :event="event"
      :connected-connector="connectedConnector"
      @close="$emit('close')"
      @edit="$root.$emit('agenda-event-form', event)"
      @delete="deleteConfirmDialog" />
    <agenda-date-poll-details-mobile-toolbar
      v-else-if="$root.$root.isMobile && isDatePoll"
      :event="event"
      @close="$emit('close')"
      @edit="$root.$emit('agenda-event-form', event)" />
    <agenda-event-details-toolbar
      v-else
      :event="event"
      :connected-connector="connectedConnector"
      :is-attendee="isAttendee"
      @close="$emit('close')"
      @edit="$root.$emit('agenda-event-form', event)"
      @delete="deleteConfirmDialog" />

    <slot name="top-bar-message"></slot>

    <v-divider class="flex-grow-0" />
    <agenda-event-date-poll-details
      v-if="isDatePoll"
      :event="event"
      @refresh-event="$emit('refresh-event')" />
    <template v-else-if="!isDatePoll">
      <agenda-event-details-body
        :settings="settings"
        :event="event"
        :connectors="connectors"
        :conference-provider="conferenceProvider" />
      <template v-if="isAttendee && $root.isMobile">
        <v-divider />
        <agenda-event-attendee-buttons
          ref="eventAttendeeButtons"
          :event="event"
          :class="$root.isMobile && 'me-1' || 'me-10'"
          class="flex-grow-0 my-2" />
      </template>
    </template>
    <agenda-recurrent-event-delete-confirm-dialog
      v-if="event.occurrence"
      ref="deleteConfirmDialog"
      :event="event" />
    <exo-confirm-dialog
      v-else
      ref="deleteConfirmDialog"
      :message="$t('agenda.message.confirmDeleteEvent')"
      :title="$t('agenda.title.confirmDeleteEvent')"
      :ok-label="$t('agenda.button.ok')"
      :cancel-label="$t('agenda.button.cancel')"
      @ok="deleteEvent" />
    <exo-confirm-dialog
      v-if="isContentEvent"
      ref="deleteContentConfirmDialog"
      :message="$t('agenda.event.content.deletion.message')"
      :title="$t('agenda.event.deletion.title')"
      :ok-label="$t('agenda.button.ok')"
      :cancel-label="$t('agenda.button.cancel')"
      @closed="cancelContentDelete"
      @ok="deleteContent" />
  </v-card>
</template>
<script>
export default {
  data() {
    return {
      confirmDialogMessage: this.$t('agenda.message.confirmDeleteEvent')
    };
  },
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
    connectors: {
      type: Array,
      default: () => []
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
  },
  computed: {
    isContentEvent() {
      return !!this.eventContentId;
    },
    eventContentId() {
      return this.event?.parameters?.contentId;
    },
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    isDatePoll(){
      return this.event && this.event.status ==='TENTATIVE';
    },
    isAttendee() {
      return this.event.acl && this.event.acl.attendee;
    },
  },
  methods: {
    deleteConfirmDialog() {
      if (this.isContentEvent) {
        this.$refs.deleteContentConfirmDialog.open();
      } else {
        this.$refs.deleteConfirmDialog.open();
      }
    },
    deleteEvent() {
      this.$eventService.deleteEvent(this.event.id, 10)
        .then(() => this.$root.$emit('agenda-event-deleted', this.event));
    },
    cancelContentDelete() {
      this.$refs.deleteConfirmDialog.open();
    },
    async deleteContent() {
      await this.$eventService.deleteContent(this.eventContentId);
      this.$refs.deleteConfirmDialog.open();
    }
  },
};
</script>
