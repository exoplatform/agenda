<template>
  <exo-drawer
    ref="quickAddEventDrawer"
    :confirm-close-labels="confirmCloseLabels"
    :confirm-close="eventChanged"
    right
    body-classes="hide-scroll decrease-z-index-more"
    class="quickAddEventDrawer"
    @closed="cancelEventCreation">
    <template slot="title">
      {{ $t('agenda.title.addEvent') }}
    </template>
    <template slot="content">
      <v-form
        v-if="event"
        ref="agendaEventForm"
        class="flex event-form quick-add"
        flat
        @submit="createEvent">
        <div class="d-flex flex-column flex-grow-1">
          <div class="d-flex flex-row width-max-content">
            <v-icon size="20" class="icon-default-color my-auto mx-3">far fa-calendar</v-icon>
            <input
              id="eventTitle"
              ref="eventTitle"
              v-model="event.summary"
              :placeholder="$t('agenda.eventTitle')"
              type="text"
              name="title"
              class="ignore-vuetify-classes my-3 ms-2 event-input"
              required
              autofocus
              @change="resetCustomValidity">
          </div>
          <div class="d-flex flex-row">
            <v-icon size="20" class="icon-default-color mt-4 mx-3">fas fa-calendar-alt</v-icon>
            <agenda-event-form-calendar-owner
              ref="calendarOwner"
              :event="event"
              :current-space="currentSpace"
              :calendars="calendars"
              class="ms-2 pe-1"
              @initialized="formInitialized" />
          </div>
          <div class="d-flex flex-row">
            <v-icon size="20" class="icon-default-color mb-auto pt-9 mx-3">fas fa-clock</v-icon>
            <agenda-event-form-date-pickers
              :event="event"
              class="pt-3 my-4 me-3 ms-2"
              @changed="updateEventDates"
              @initialized="formInitialized" />
          </div>
          <div class="d-flex flex-row">
            <v-icon size="20" class="icon-default-color my-auto mx-3">fas fa-map-marker-alt</v-icon>
            <input
              id="eventLocation"
              ref="eventLocation"
              v-model="event.location"
              :placeholder="$t('agenda.eventLocation')"
              type="text"
              name="locationEvent"
              class="ignore-vuetify-classes my-3 ms-3 event-input">
          </div>
          <agenda-event-form-conference
            :event="event"
            :settings="settings"
            :current-space="currentSpace"
            :conference-provider="conferenceProvider"
            icon-class="ms-3 me-4" />
          <div class="d-flex flex-row">
            <v-icon size="20" class="icon-default-color mb-auto pt-6 ms-3 me-4">fas fa-users</v-icon>
            <agenda-event-form-attendees
              :event="event"
              class="ml-n2px pe-1"
              @initialized="formInitialized" />
          </div>
        </div>
      </v-form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-btn
          class="btn ms-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-spacer />
        <v-btn
          class="btn ms-2"
          @click="openCompleteEventForm">
          {{ $t('agenda.button.moreDetails') }}
        </v-btn>
        <v-btn
          :disabled="disableSaveButton"
          class="btn btn-primary ms-2"
          @click="createEvent">
          {{ $t('agenda.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: () => null,
    },
    calendars: {
      type: Array,
      default: () => []
    },
    settings: {
      type: Object,
      default: () => null,
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    event: null,
    originalEventString: null,
    saving: false,
    conferenceURL: null,
  }),
  computed: {
    confirmCloseLabels() {
      return {
        title: this.$t('agenda.title.confirmCloseEditingEvent'),
        message: this.$t('agenda.message.confirmCloseEditingEvent'),
        ok: this.$t('agenda.button.ok'),
        cancel: this.$t('agenda.button.cancel'),
      };
    },
    eventChanged() {
      return !this.saving && this.event && this.originalEventString && this.originalEventString !== JSON.stringify(this.event);
    },
    eventTitle() {
      return this.event && this.event.summary;
    },
    eventTitleValid() {
      return this.eventTitle && this.eventTitle.length >= 1 && this.eventTitle.length < 1024;
    },
    eventOwner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
    },
    eventOwnerValid() {
      return this.eventOwner && (this.eventOwner.id || this.eventOwner.remoteId && this.eventOwner.providerId);
    },
    disableSaveButton() {
      return this.saving || !this.eventTitleValid || !this.eventOwnerValid;
    },
    isConferenceEnabled() {
      return this.conferenceProvider && this.conferenceProvider.getType();
    },
  },
  watch: {
    conferenceURL(newVal) {
      if (!newVal) {
        this.event.conferences = [];
      } else {
        this.event.conferences = [{
          url: newVal,
          type: 'manual',
        }];
      }
    },
    eventOwner(newVal) {
      if (newVal && newVal.providerId) {
        this.$identityService.getIdentityByProviderIdAndRemoteId(newVal.providerId, newVal.remoteId)
          .then(identity => {
            this.event.calendar.owner.id = identity.id;
          });
      }
    }
  },
  created() {
    this.$root.$on('agenda-event-quick-form', event => {
      this.event = null;
      this.$nextTick().then(() => {
        this.event = event;
        if (!this.event.timeZoneId) {
          this.event.timeZoneId = this.$agendaUtils.USER_TIMEZONE_ID;
        }
        this.open();
        this.$nextTick().then(() => this.$root.$emit('agenda-event-form-opened', this.event));
      });
    });
  },
  methods: {
    close() {
      this.conferenceURL = null;
      this.$refs.quickAddEventDrawer.close();
    },
    open() {
      if (this.$root.timelineSettings && this.$root.timelineSettings.agendaSource === 'selectedSpaces' && (!this.calendars?.length || (this.calendars[0] && !this.calendars.some(c => c?.acl?.canCreate)))) {
        return;
      }
      this.resetCustomValidity();
      this.$refs.quickAddEventDrawer.open();
      window.setTimeout(() => {
        if (this.$refs.eventTitle) {
          this.$refs.eventTitle.focus();
        }
      }, 500);
    },
    formInitialized() {
      this.originalEventString = JSON.stringify(this.event);
    },
    openCompleteEventForm() {
      this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);

      this.$root.$emit('agenda-event-form', this.event, true);
      this.cancelEventCreation();
      this.$nextTick(() => this.$refs.quickAddEventDrawer.close());
    },
    resetCustomValidity() {
      if (this.$refs.eventTitle) {
        this.$refs.eventTitle.setCustomValidity('');
      }
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.resetCustomValidity();
      }
    },
    updateEventDates(event) {
      event.startDate = new Date(event.startDate);
      event.endDate = new Date(event.endDate);
    },
    validateForm() {
      this.resetCustomValidity();
      this.$refs.calendarOwner.validateForm();

      if (!this.event.summary) {
        this.$refs.eventTitle.setCustomValidity(this.$t('agenda.message.missingEventTitle'));
      } else if (this.event.summary.trim().length < 1 || this.event.summary.trim().length > 1024) {
        this.$refs.eventTitle.setCustomValidity(this.$t('agenda.message.missingLengthEventTitle'));
      }

      if (!this.$refs.agendaEventForm.validate() // Vuetify rules
          || !this.$refs.agendaEventForm.$el.reportValidity()) { // Standard HTML rules
        return;
      }

      return true;
    },
    cancelEventCreation() {
      this.event = null;
      this.$root.$emit('agenda-event-quick-form-cancel');
    },
    createEvent() {
      if (!this.validateForm()) {
        return;
      }
      this.saving = true;

      this.event.startDate = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.endDate = this.$agendaUtils.toRFC3339(this.event.endDate);

      if (!this.event.start) {
        this.event.start = this.event.startDate || this.$agendaUtils.toRFC3339(new Date());
      }
      if (!this.event.end) {
        this.event.end = this.event.endDate || this.$agendaUtils.toRFC3339(new Date());
      }

      this.$eventService.createEvent(this.event)
        .then(event => {
          this.$root.$emit('agenda-event-saved', event);
          this.close();
        })
        .finally(() => {
          this.saving = false;
          this.$root.$emit('agenda-refresh');
        });
    },
  }
};
</script>
