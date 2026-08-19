<template>
  <v-form
    ref="agendaEventForm"
    class="flex"
    flat
    @submit="$emit('next-step')">
    <div class="d-flex flex-column flex-md-row mt-1 event-form-body">
      <div class="d-flex flex-column flex-grow-1 event-form-body-left">
        <div class="d-flex flex-row">
          <v-icon size="20" class="icon-default-color my-auto me-12 d-none d-md-inline">far fa-calendar</v-icon>
          <input
            id="eventTitle"
            ref="eventTitle"
            v-model="event.summary"
            :placeholder="$t('agenda.eventTitle')"
            type="text"
            name="title"
            class="ignore-vuetify-classes my-3 event-input"
            required
            @change="resetCustomValidity">
        </div>
        <!--
          The destination is a block of one field per line under the title
          (select, then the space suggester when the space flow is chosen),
          every field carrying the same event-input width as the title and
          location — one column of equal fields. The 'in' lead-in died with
          the single-line sentence: the select's values already read as
          destinations. The single gutter icon mirrors the drawer's
          destination icon and, like there, labels the destination as a
          whole: anchored to the first field, the suggester line keeps an
          empty gutter so the block reads as one destination.
        -->
        <div class="d-flex flex-row">
          <v-icon
            size="20"
            class="icon-default-color mb-auto mt-5 me-12 d-none d-md-inline">
            fas fa-calendar-alt
          </v-icon>
          <agenda-event-form-destination
            ref="calendarOwner"
            :event="event"
            :current-space="currentSpace"
            inline
            @initialized="$emit('initialized')" />
        </div>
        <div v-if="displayTimeInForm && eventDateOption" class="d-flex flex-row">
          <v-icon size="20" class="icon-default-color mb-auto pt-6 me-11">fas fa-clock</v-icon>
          <agenda-event-form-date-pickers
            :event="eventDateOption"
            class="my-4"
            compact
            @changed="updateEventDates"
            @initialized="formInitialized" />
        </div>
        <div class="d-flex flex-row">
          <v-icon size="20" class="icon-default-color my-auto me-12">fas fa-map-marker-alt</v-icon>
          <input
            id="eventLocation"
            ref="eventLocation"
            v-model="event.location"
            :placeholder="$t('agenda.eventLocation')"
            type="text"
            name="locationEvent"
            class="ignore-vuetify-classes my-3 event-input">
        </div>
        <div class="d-flex flex-row align-center">
          <v-icon size="20" class="icon-default-color my-auto me-12">fas fa-palette</v-icon>
          <agenda-event-form-color-picker :event="event" :calendars="selectedCalendars" />
        </div>
        <div class="d-flex flex-row">
          <div :class="hasRecurrence ? 'flex-grow-0 pt-2' : 'my-auto'">
            <v-icon size="20" class="icon-default-color me-11">fas fa-redo</v-icon>
          </div>
          <div class="d-flex flex-column">
            <agenda-event-form-recurrence :event="event" class="my-auto" />
            <agenda-event-recurrence
              v-if="hasRecurrence"
              :event="event"
              class="text-wrap ms-2" />
          </div>
        </div>
        <agenda-event-form-conference
          :event="event"
          :settings="settings"
          :current-space="currentSpace"
          :conference-provider="conferenceProvider"
          class="my-auto"
          icon-class="me-10" />
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0">
            <v-icon size="20" class="icon-default-color mt-3 me-11">fas fa-align-left</v-icon>
          </v-flex>
          <rich-editor
            id="eventDescription"
            ref="eventDescription"
            v-model="event.description"
            :placeholder="$t('agenda.descriptionPlaceholder')"
            :max-length="eventDescriptionTextLength"
            :tag-enabled="false"
            ck-editor-type="agendaEventDescription"
            class="pt-3 width-full"
            content-link-enabled />
        </div>
      </div>
      <div class="d-none d-md-flex flex-column mx-5 event-form-body-divider ">
        <v-divider vertical />
      </div>
      <div class="d-flex flex-column flex-shrink-0 event-form-body-right">
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0 me-2 my-2">
            <v-icon size="20" class="icon-default-color m-auto">fas fa-users</v-icon>
          </v-flex>
          <agenda-event-form-attendees
            :event="event"
            class="ms-4"
            @initialized="$emit('initialized')" />
        </div>
        <div class="d-flex flex-row">
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.modifyEventPermission') }}</label>
          <v-switch
            ref="allowAttendeeToUpdateRef"
            v-model="event.allowAttendeeToUpdate"
            :disabled="!canInviteeEdit"
            class="mt-0 ms-4" />
        </div>
        <div class="d-flex flex-row font-weight-regular">
          {{ $t('agenda.modifyEventPermissionDescription') }}
        </div>
      </div>
    </div>
  </v-form>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    displayTimeInForm: {
      type: Boolean,
      default: false,
    },
    currentSpace: {
      type: Object,
      default: () => null,
    },
    selectedCalendar: {
      type: Object,
      default: () => null,
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
    eventDescriptionTextLength: 1300,
    canInviteeEdit: true,
    eventDateOption: null,
  }),
  computed: {
    allowAttendeeToUpdate() {
      return this.event.allowAttendeeToUpdate;
    },
    hasRecurrence() {
      return this.event.recurrence || this.event.parent && this.event.parent.recurrence;
    },
    eventDateOptions() {
      return this.event && this.event.dateOptions || [];
    },
    selectedCalendars() {
      return this.selectedCalendar && [this.selectedCalendar] || [];
    },
  },
  watch: {
    displayTimeInForm() {
      this.reset();
    },
    eventDateOptions() {
      this.reset();
    },
    allowAttendeeToUpdate() {
      if (this.allowAttendeeToUpdate) {
        this.event.allowAttendeeToInvite = true;
      }
    },
    selectedCalendar() {
      this.canInviteeEdit = !this.selectedCalendar || !this.selectedCalendar.acl || this.selectedCalendar.acl.canInviteeEdit;
      if (!this.canInviteeEdit && this.selectedCalendar) {
        this.event.allowAttendeeToUpdate = false;
        this.$forceUpdate();
      }
    },
    canInviteeEdit() {
      if (!this.canInviteeEdit && this.selectedCalendar) {
        this.event.allowAttendeeToUpdate = false;
        this.$forceUpdate();
      }
    },
  },
  created() {
    this.$root.$once('agenda-event-form-opened', () => {
      this.$nextTick().then(this.resetCustomValidity);
    });
  },
  mounted() {
    window.setTimeout(() => {
      if (this.$refs.eventTitle) {
        this.$refs.eventTitle.focus();
      }
    }, 500);
  },
  methods: {
    /**
     * Resets the date option bound to the date pickers from the event being
     * edited, once the pickers are unmounted and remounted.
     * @returns {void}
     */
    reset() {
      this.eventDateOption = null;
      this.$nextTick().then(() => {
        this.eventDateOption = this.event.dateOptions.length === 1 && this.event.dateOptions[0] || this.event;
      });
    },
    /**
     * Clears any custom validity set on the title field and the destination.
     * @returns {void}
     */
    resetCustomValidity() {
      if (this.$refs.eventTitle) {
        this.$refs.eventTitle.setCustomValidity('');
      }
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.resetCustomValidity();
      }
    },
    /**
     * Propagates the picked start and end dates from the date pickers to the
     * event payload, mirrored to its single date option when there is one.
     * @returns {void}
     */
    updateEventDates() {
      this.event.startDate = new Date(this.eventDateOption.startDate);
      this.event.endDate = new Date(this.eventDateOption.endDate);

      this.event.start = this.$agendaUtils.toRFC3339(this.event.startDate);
      this.event.end = this.$agendaUtils.toRFC3339(this.event.endDate);

      if (this.event.dateOptions && this.event.dateOptions.length === 1) {
        this.event.dateOptions[0].startDate = new Date(this.event.startDate);
        this.event.dateOptions[0].endDate = new Date(this.event.endDate);
        this.event.dateOptions[0].start = this.event.start;
        this.event.dateOptions[0].end = this.event.end;
      }
    },
    /**
     * Validates the basic information step: the title presence and length,
     * the destination, then the Vuetify and standard HTML form rules.
     *
     * @returns {Boolean} true when the form is valid, undefined otherwise
     */
    validateForm() {
      this.resetCustomValidity();
      this.$refs.calendarOwner.validateForm();

      if (!this.event.summary) {
        this.$refs.eventTitle.setCustomValidity(this.$t('agenda.message.missingEventTitle'));
      } else if (this.event.summary.length < 1 || this.event.summary.length > 1024) {
        this.$refs.eventTitle.setCustomValidity(this.$t('agenda.message.missingLengthEventTitle'));
      }

      if (!this.$refs.agendaEventForm.validate() // Vuetify rules
          || !this.$refs.agendaEventForm.$el.reportValidity()) { // Standard HTML rules
        return;
      }

      return true;
    },
  }
};
</script>
