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
  data: () => ({
    confirmDeleteEvent: false,
  }),
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
  created() {
    this.$root.$on('confirm-delete-event', (data) => {
      this.confirmDeleteEvent = data;
    });
  },
  methods:{
    deleteEvent() {
      this.$eventService.updateEventFields(this.event.id, {
        status :'CANCELLED',
      }, false, true)
        .then(() => {
          this.$eventService.sendEventResponse(this.event.id, null, 'ACCEPTED').then(() =>
            this.$root.$emit('agenda-event-response-sent', this.event, null, 'ACCEPTED')
          );
          this.$root.$emit('agenda-refresh', this.event);
          this.$root.$emit('event-canceled', this.event);
          this.$emit('close');
        }).then(()=>{
          if(this.confirmDeleteEvent) {
            window.setTimeout(() =>this.$eventService.deleteEvent(this.event.id),10000);
            this.confirmDeleteEvent = true;
          }
        });
    },
    deleteConfirmDialog() {
      if (!this.isDatePoll) {
        this.$refs.deleteConfirmDialog.open();
      } else {
        this.$eventService.updateEventFields(this.event.id, {
          status :'CANCELLED'
        }, false, true)
          .then(() => {
            this.$root.$emit('event-canceled', this.event);
            this.$emit('close');
          }).then(()=>{
            if(this.confirmDeleteEvent) {
              window.setTimeout(() =>this.$eventService.deleteEvent(this.event.id),10000);
              this.this.confirmDeleteEvent = true;
            }
          });
      }
    },
  },
};
</script>