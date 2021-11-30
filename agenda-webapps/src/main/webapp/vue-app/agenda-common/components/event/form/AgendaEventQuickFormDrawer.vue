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
          <div class="d-flex flex-row">
            <input
              id="eventTitle"
              ref="eventTitle"
              v-model="event.summary"
              :placeholder="$t('agenda.eventTitle')"
              type="text"
              name="title"
              class="ignore-vuetify-classes my-3"
              required
              autofocus
              @change="resetCustomValidity">
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0">
              <i class="uiIconPLFEventTask darkGreyIcon uiIcon32x32 mt-4 mx-3"></i>
            </v-flex>
            <agenda-event-form-calendar-owner
              ref="calendarOwner"
              :event="event"
              :current-space="currentSpace"
              class="ms-1 pe-1"
              @initialized="formInitialized" />
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0 pt-4 my-2 mx-3">
              <i class="uiIconClock darkGreyIcon uiIcon32x32"></i>
            </v-flex>
            <agenda-event-form-date-pickers
              :event="event"
              class="pt-3 my-4 me-3"
              @changed="updateEventDates"
              @initialized="formInitialized" />
          </div>
          <div class="d-flex flex-row">
            <i class="uiIconLocation darkGreyIcon uiIcon32x32 mt-4 mx-3"></i>
            <input
              id="eventLocation"
              ref="eventLocation"
              v-model="event.location"
              :placeholder="$t('agenda.eventLocation')"
              type="text"
              name="locationEvent"
              class="ignore-vuetify-classes my-3 location-event-input">
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0 my-auto mx-3">
              <i class="uiIconVideo darkGreyIcon uiIcon32x32"></i>
            </v-flex>
            <agenda-event-form-conference
              v-if="isConferenceEnabled"
              :event="event"
              :settings="settings"
              :current-space="currentSpace"
              :conference-provider="conferenceProvider"
              class="me-3" />
            <input
              v-else
              id="eventConference"
              ref="eventConference"
              v-model="conferenceURL"
              :placeholder="$t('agenda.webConferenceURL')"
              type="text"
              name="locationEvent"
              class="ignore-vuetify-classes my-3 location-event-input">
          </div>
          <div class="d-flex flex-row">
            <v-flex class="flex-grow-0">
              <i class="uiIconGroup darkGreyIcon uiIcon32x32 mt-4 mx-3"></i>
            </v-flex>
            <agenda-event-form-attendees
              :event="event"
              class="pe-1"
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
    currentCalendar: {
      type: Object,
      default: () => null
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
      return this.eventTitle && this.eventTitle.length >= 5 && this.eventTitle.length < 1024;
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
      if (newVal.providerId) {
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
      if (this.currentCalendar && this.currentCalendar.acl && !this.currentCalendar.acl.canCreate) {
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
      } else if (this.event.summary.trim().length < 5 || this.event.summary.trim().length > 1024) {
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

      this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);
      delete this.event.startDate;
      delete this.event.endDate;

      this.$eventService.createEvent(this.event)
        .then(event => {
          this.$root.$emit('agenda-event-saved', event);
          this.close();
        })
        .finally(() => this.saving = false);
    },
  }
};
</script>
