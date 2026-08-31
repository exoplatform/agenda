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
            <!--
              mb-auto anchors the icon to the top of the row: it labels the
              destination as a whole, so it lines up with the row's first
              field (the select) instead of centring across the pair when the
              space suggester adds a second field below — and it does not
              move when the suggester is absent (personal destination)
            -->
            <v-icon size="20" class="icon-default-color mb-auto mt-5 mx-3">fas fa-calendar-alt</v-icon>
            <!--
              event-input makes the destination box the title field's exact
              twin: same 500px bound and, through the drawer's shared
              calc(100% - 64px) inset, the same right edge — replacing the
              ad-hoc pe-1 that let it run wider than every input
            -->
            <agenda-event-form-destination
              ref="calendarOwner"
              :event="event"
              :current-space="currentSpace"
              :calendars="calendars"
              class="ms-2 event-input"
              @initialized="formInitialized" />
          </div>
          <div class="d-flex flex-row">
            <v-icon size="20" class="icon-default-color mb-auto pt-9 mx-3">fas fa-clock</v-icon>
            <agenda-event-form-date-pickers
              :event="event"
              class="pt-3 my-4 me-3 ms-2"
              compact
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
          <!--
            Every block below carries the fields' own my-3 rhythm, so the
            whitespace between any two blocks reads the same 24px whether the
            content is a 40px field or a one-line label — no block rule, no
            per-block numbers
          -->
          <agenda-event-form-conference
            :event="event"
            :settings="settings"
            :current-space="currentSpace"
            :conference-provider="conferenceProvider"
            class="my-3"
            icon-class="ms-3 me-4" />
          <div class="d-flex flex-row align-center my-3">
            <v-icon size="20" class="icon-default-color my-auto ms-3 me-5">fas fa-palette</v-icon>
            <agenda-event-form-color-picker :event="event" :calendars="calendars" />
          </div>
          <div class="d-flex flex-row align-center my-3">
            <v-icon size="20" class="icon-default-color my-auto ms-3 me-4">fas fa-users</v-icon>
            <agenda-event-form-attendees
              :event="event"
              class="ml-n2px pe-1"
              @initialized="formInitialized" />
          </div>
          <!--
            The only trace of the date poll on the drawer's path, and a link
            rather than a sentence: this drawer is deliberately minimal, and
            an explanation here would fight that — the explanation lives in
            the full form, which has room for it. The words are the full
            form's own button label, read from the same key, so the two
            places teach one name for one feature.

            Not a footer button: the footer already carries three, and a
            fourth would read as a fourth way to commit rather than as a
            change of route.

            Disabled it is greyed and says nothing further. The two fields it
            waits on — the title and the destination — sit directly above it,
            so what is missing is visible without being narrated, and a
            sentence spelling that out would cost this drawer the minimalism
            that is the whole reason the explanation lives in the full form.

            Not on mobile. This drawer is reachable there (the empty timeline
            opens it), but the dialog behind it renders the mobile form, which
            has no date step and cannot create a poll at all — out of scope
            here, deliberately. A link advertising a feature and landing on a
            screen that does not have it is worse than the silence it replaced.
          -->
          <div
            v-if="!$root.isMobile"
            class="d-flex flex-row align-center my-3 ms-3">
            <v-icon size="20" class="icon-default-color my-auto me-4">far fa-calendar-check</v-icon>
            <a
              v-if="datePollRouteEnabled"
              href="#"
              class="primary--text"
              @click.prevent="openDatePollForm">
              {{ $t('agenda.alternativeDates') }}
            </a>
            <span v-else class="text-light-color">{{ $t('agenda.alternativeDates') }}</span>
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
    /*
     * The date-poll link opens the full form on its date step, so it may only
     * admit whom that step admits. That is the form's disableNextStepButton
     * rule, held once in AgendaUtils and applied here rather than restated:
     * were it restated, the two could drift and this link would open a step
     * the form itself refuses to leave.
     */
    datePollRouteEnabled() {
      return !this.saving && this.$agendaUtils.isEventDetailsComplete(this.event, this.$utils.htmlToText);
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
    /**
     * Opens the full event form on its details step — the drawer's historical
     * "More details" route.
     *
     * @returns {void}
     */
    openCompleteEventForm() {
      this.openFullEventForm(false);
    },
    /**
     * Opens the full event form directly on its date step — the drawer's route
     * to the date poll, gated on datePollRouteEnabled.
     *
     * @returns {void}
     */
    openDatePollForm() {
      this.openFullEventForm(true);
    },
    /**
     * Hands the half-filled event over to the full form and closes the drawer.
     *
     * The flag is passed explicitly rather than taken from the click handler's
     * argument: both callers are @click handlers, and a DOM event read as the
     * flag would send "More details" to the date step too.
     *
     * @param {Boolean} openDateOptions true to open on the date step
     * @returns {void}
     */
    openFullEventForm(openDateOptions) {
      this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);

      this.$root.$emit('agenda-event-form', this.event, true, openDateOptions);
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
