<template>
  <v-card
    v-if="event"
    flat
    class="event-details d-flex flex-column">
    <agenda-event-details-mobile-toolbar
      v-if="isMobile"
      :event="event"
      :connected-connector="connectedConnector"
      :is-attendee="isAttendee"
      @close="$emit('close')"
      @edit="$root.$emit('agenda-event-form', event)"
      @delete="deleteConfirmDialog" />
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
      :settings="settings"
      :event="event"
      :connectors="connectors"
      :conference-provider="conferenceProvider" />
    <agenda-event-details-body
      v-else
      :settings="settings"
      :event="event"
      :connectors="connectors"
      :conference-provider="conferenceProvider" />
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
  </v-card>
</template>
<script>
export default {
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
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    isDatePoll(){
      return this.event && this.event.status ==='TENTATIVE';
    },
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    isAttendee() {
      return this.event.acl && this.event.acl.attendee;
    },
  },
  methods:{
    deleteConfirmDialog() {
      this.$refs.deleteConfirmDialog.open();
    },
    deleteEvent() {
      this.$eventService.deleteEvent(this.event.id, 10000)
        .then(() => this.$root.$emit('agenda-event-deleted', this.event));
    },
  },
};
</script>