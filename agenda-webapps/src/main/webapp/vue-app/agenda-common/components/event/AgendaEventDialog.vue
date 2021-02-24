<template>
  <v-dialog
    ref="eventDialog"
    v-model="dialog"
    persistent
    fullscreen
    hide-overlay>
    <template v-if="event && dialog">
      <template v-if="isForm">
        <agenda-event-mobile-form
          v-if="isMobile"
          ref="eventForm"
          :event="event"
          :current-space="currentSpace"
          class="fill-height event-form"
          @close="close"
          @saved="saved"
          @initialized="formInitialized" />
        <agenda-event-form
          v-else
          ref="eventForm"
          :settings="settings"
          :event="event"
          :display-time-in-form="displayTimeInForm"
          :weekdays="weekdays"
          :current-space="currentSpace"
          :working-time="workingTime"
          :connectors="connectors"
          :conference-provider="conferenceProvider"
          class="fill-height event-form"
          @close="close"
          @saved="saved"
          @initialized="formInitialized" />
        <exo-confirm-dialog
          ref="closeConfirmDialog"
          :title="confirmCloseLabels.title"
          :message="confirmCloseLabels.message"
          :ok-label="confirmCloseLabels.ok"
          :cancel-label="confirmCloseLabels.cancel"
          persistent
          @ok="closeEffectively" />
      </template>
      <agenda-event-details
        v-else-if="isTentativeEvent || isConfirmedEvent"
        ref="eventDetails"
        :settings="settings"
        :conference-provider="conferenceProvider"
        :event="event"
        :weekdays="weekdays"
        :connectors="connectors"
        @close="close">
        <template slot="top-bar-message">
          <v-alert
            v-model="hasMessage"
            :type="messageType"
            class="mb-0"
            dismissible>
            {{ message }}
          </v-alert>
        </template>
      </agenda-event-details>
    </template>
  </v-dialog>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    weekdays: {
      type: Array,
      default: () => null
    },
    workingTime: {
      type: Object,
      default: () => null
    },
    currentSpace: {
      type: Object,
      default: () => null
    },
    currentCalendar: {
      type: Object,
      default: () => null
    },
    connectors: {
      type: Array,
      default: () => null,
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
  },
  data () {
    return {
      isNew: false,
      dialog: false,
      saving: false,
      event: null,
      loadingMessage: false,
      hasMessage: null,
      message: null,
      messageType: null,
      originalEventString: null,
      isForm: false,
      displayTimeInForm: false,
    };
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    isModified() {
      return !this.saving && this.isForm && this.event && this.originalEventString && this.originalEventString !== JSON.stringify(this.event);
    },
    isConfirmedEvent() {
      return this.event && this.event.status === 'CONFIRMED';
    },
    isTentativeEvent() {
      return this.event && this.event.status === 'TENTATIVE';
    },
    confirmCloseLabels() {
      return {
        title: this.$t('agenda.title.confirmCloseEditingEvent'),
        message: this.$t('agenda.message.confirmCloseEditingEvent'),
        ok: this.$t('agenda.button.ok'),
        cancel: this.$t('agenda.button.cancel'),
      };
    },
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
  },
  watch: {
    dialog() {
      if (this.dialog) {
        if (!this.isMobile) {
          $('body').addClass('hide-scroll');
        }
      } else {
        if (!this.isMobile) {
          setTimeout(() => $('body').removeClass('hide-scroll'), 200);
        }
        this.event = null;
      }
    },
  },
  created() {
    const search = document.location.search.substring(1);
    if(search) {
      const parameters = JSON.parse(
        `{"${decodeURI(search)
          .replace(/"/g, '\\"')
          .replace(/&/g, '","')
          .replace(/=/g, '":"')}"}`
      );
      const eventId = parameters.eventId && Number(parameters.eventId) || 0;
      const parentId = parameters.parentId && Number(parameters.parentId) || 0;
      const occurrenceId = parameters.occurrenceId;
      if (eventId) {
        this.openEventDetails(eventId);
      } else if (parentId && occurrenceId) {
        this.openEventDetails(parentId, occurrenceId);
      }
    }
    $(document).on('keydown', this.closeByEscape);
    this.$root.$on('agenda-event-form', (agendaEvent, displayTimeInForm) => {
      this.isNew = agendaEvent.id ? !agendaEvent.id : !agendaEvent.parent || !agendaEvent.parent.id;
      if (this.isNew) {
        if (this.currentCalendar && this.currentCalendar.acl && !this.currentCalendar.acl.canCreate) {
          return;
        }
        this.isForm = true;
        this.displayTimeInForm = !!displayTimeInForm;
        this.openDialog(agendaEvent);
        this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', agendaEvent));
      } else {
        this.displayTimeInForm = true;
        if (agendaEvent.id) {
          this.openEventForm(agendaEvent.id);
        } else if (agendaEvent.occurrence && agendaEvent.occurrence.id) {
          this.openEventForm(agendaEvent.parent.id, agendaEvent.occurrence.id);
        }
      }
    });
    this.$root.$on('agenda-event-details', agendaEvent => {
      if (agendaEvent.id) {
        this.openEventDetails(agendaEvent.id);
      } else if (agendaEvent.occurrence && agendaEvent.occurrence.id) {
        this.openEventDetails(agendaEvent.parent.id, agendaEvent.occurrence.id);
      }
    });
    this.$root.$on('agenda-event-deleted', () => this.close());
    this.$root.$on('agenda-event-save', () => this.saving = true);
    this.$root.$on('agenda-event-saved', () => this.close());
    this.$root.$on('agenda-event-response-sent', (event, occurrenceId, eventResponse) => {
      if (!this.event) {
        return;
      }
      const retrieveEventDetailsPromise = this.event.occurrence && this.event.occurrence.id ? this.$eventService.getEventOccurrence(this.event.parent.id, this.event.occurrence.id, 'all') : this.$eventService.getEventById(this.event.id, 'all');
      retrieveEventDetailsPromise
        .then(event => this.event = event)
        .finally(() => {
          const updatedEvent = event.recurrence ? this.event.parent : this.event;
          this.$root.$emit('agenda-event-response-updated', updatedEvent, occurrenceId, eventResponse);
        });
    });
    this.$root.$on('agenda-event-reminders-saved', (event, occurrenceId, reminders) => {
      this.event.reminders = reminders;
      if (event.id && !this.event.id) {
        this.$root.$emit('agenda-refresh');
      }
    });
    this.$root.$on('agenda-remote-event-pushed', () => {
      this.displayMessage('success', this.$t('agenda.eventPushedSuccess'));
      if (this.event) {
        const eventId = this.event.id;
        const parentId = this.event.parent && this.event.parent.id || 0;
        const occurrenceId = this.event.occurrence && this.event.occurrence.id;
        if (eventId) {
          this.openEventDetails(eventId);
        } else if (parentId && occurrenceId) {
          this.openEventDetails(parentId, occurrenceId);
        }
      }
    });
    this.$root.$on('agenda-remote-event-push-error', (event, error) => {
      console.error('Error pushing event', error);
      this.displayMessage('error', this.$t('agenda.errorPushingEvent'));
    });
  },
  methods: {
    closeByEscape(event) {
      if (event.key === 'Escape' && this.dialog) {
        this.close(event);
      }
    },
    formInitialized() {
      this.originalEventString = JSON.stringify(this.event);
    },
    openEventForm(eventId, occurrenceId) {
      this.isForm = true;
      this.openEventById(eventId, occurrenceId);
    },
    openEventDetails(eventId, occurrenceId) {
      this.isForm = false;
      return this.openEventById(eventId, occurrenceId);
    },
    openEventById(eventId, occurrenceId) {
      if (eventId) {
        const getEventDetailsPromise = occurrenceId ? this.$eventService.getEventOccurrence(eventId, occurrenceId, 'all') : this.$eventService.getEventById(eventId, 'all');
        return getEventDetailsPromise.then(event => {
          if(!event ||  (event.status !== 'TENTATIVE'&& event.status !=='CONFIRMED')) {
            return ;
          }
          event.startDate = this.$agendaUtils.toDate(event.start);
          event.endDate = this.$agendaUtils.toDate(event.end);
  
          this.openDialog(event);
          if (this.isForm) {
            this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', event));
          } else {
            this.$nextTick().then(() => this.$root.$emit('agenda-event-details-opened', event));
          }
          return event;
        });
      } else {
        return Promise.resolve(null);
      }
    },
    openDialog(agendaEvent) {
      this.saving = false;
      if (!agendaEvent) {
        agendaEvent = {};
      }
      if (!agendaEvent.calendar) {
        agendaEvent.calendar = {};
      }
      if (!agendaEvent.calendar.owner) {
        agendaEvent.calendar.owner = {};
      }
      if (!agendaEvent.reminders) {
        agendaEvent.reminders = [];
      }
      if (!agendaEvent.attachments) {
        agendaEvent.attachments = [];
      }
      if (!agendaEvent.attendees) {
        agendaEvent.attendees = [];
      }
      this.event = agendaEvent;

      let eventDetailsPath = `${window.location.pathname}`;
      if (this.event.id) {
        eventDetailsPath = `${eventDetailsPath}?eventId=${this.event.id}`;
      } else if (this.event.parent && this.event.parent.id && this.event.occurrence && this.event.occurrence.id) {
        eventDetailsPath = `${eventDetailsPath}?parentId=${this.event.parent.id}&occurrenceId=${this.event.occurrence.id}`;
      }
      window.history.replaceState('', window.document.title, eventDetailsPath);
      this.dialog = true;
    },
    close(event) {
      if (this.isModified) {
        if (this.$refs.closeConfirmDialog) {
          if (event) {
            event.preventDefault();
            event.stopPropagation();
          }

          if (this.$refs.closeConfirmDialog.dialog) {
            this.$nextTick(this.$refs.closeConfirmDialog.close);
          } else {
            this.$nextTick(this.$refs.closeConfirmDialog.open);
          }
        }
      } else {
        if (event) {
          event.preventDefault();
          event.stopPropagation();
        }

        this.closeEffectively();
      }
    },
    closeEffectively() {
      // Workaround to close titptip when closing dialog
      $('#tiptip_holder').mouseleave();

      this.dialog = false;
      window.history.replaceState('', window.document.title, window.location.pathname);
    },
    displayMessage(type, message) {
      this.messageType = type;
      this.message = message;
      this.hasMessage = true;

      window.setTimeout(() => this.hasMessage = false, 5000);
    }
  },
};
</script>