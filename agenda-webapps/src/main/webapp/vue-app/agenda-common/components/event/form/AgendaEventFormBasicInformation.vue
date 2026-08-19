<template>
  <v-form
    ref="agendaEventForm"
    class="flex"
    flat
    @submit="$emit('next-step')">
    <div class="d-flex flex-column flex-md-row mt-1 event-form-body">
      <div class="d-flex flex-column flex-grow-1 event-form-body-left">
        <!--
          The destination row wraps on its own available width, not on the
          viewport: the side panel (participants / modify event) eats into
          this column, so a wide window can still be too narrow here. As soon
          as the three fields would no longer fit, the whole row switches to
          the drawer's layout (title on its own line, select beneath,
          suggester beneath that) instead of wrapping one field at a time:
          the destination drops its inline mode and takes the full width on
          the next flex-wrap line, under the icon + title pair.
        -->
        <div
          ref="destinationRow"
          :class="!destinationInline && 'flex-wrap'"
          class="d-flex flex-row">
          <v-icon size="20" class="icon-default-color my-auto me-12 d-none d-md-inline">far fa-calendar</v-icon>
          <input
            id="eventTitle"
            ref="eventTitle"
            v-model="event.summary"
            :placeholder="$t('agenda.eventTitle')"
            :class="destinationInline && 'destination-row-field'"
            type="text"
            name="title"
            class="ignore-vuetify-classes my-3"
            required
            @change="resetCustomValidity">
          <label
            v-if="destinationInline"
            class="mt-5 ms-4 me-4 text-subtitle-1 font-weight-bold">
            {{ $t('agenda.label.in') }}
          </label>
          <agenda-event-form-destination
            ref="calendarOwner"
            :event="event"
            :current-space="currentSpace"
            :inline="destinationInline"
            :class="!destinationInline && 'full-width'"
            @initialized="$emit('initialized')" />
        </div>
        <div v-if="displayTimeInForm && eventDateOption" class="d-flex flex-row">
          <v-icon size="20" class="icon-default-color mb-auto pt-6 me-11">fas fa-clock</v-icon>
          <agenda-event-form-date-pickers
            :event="eventDateOption"
            class="event-form-datetimes my-4"
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
/**
 * Minimum width (px) of the destination row for the three fields to fit on
 * one line. The fields share the row's width equally (destination-row-field
 * in agenda.less), so the floor is a usable field of ~220px each: calendar
 * icon + gap (68) + 'in' label with margins (~52) + suggester gap (16) +
 * 3 x 220. Below it the row switches wholesale to the drawer's stacked
 * layout.
 */
const DESTINATION_INLINE_MIN_WIDTH = 800;

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
    rowFitsInline: true,
  }),
  computed: {
    /**
     * Whether the destination row renders inline (title, select and space
     * suggester side by side) or stacked like the drawer. Driven by the
     * row's own measured width — not the viewport, which cannot account for
     * the side panel — combined with the breakpoint that already gates the
     * icon and 'in' label.
     *
     * @returns {Boolean} true when the inline layout fits
     */
    destinationInline() {
      return this.rowFitsInline && this.$vuetify.breakpoint.mdAndUp;
    },
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
    if (window.ResizeObserver && this.$refs.destinationRow) {
      // The observer only reads the row's width and the layout switch only
      // changes its height, so a switch never retriggers itself
      this.destinationRowObserver = new window.ResizeObserver(
        entries => this.updateDestinationLayout(entries[0].contentRect.width));
      this.destinationRowObserver.observe(this.$refs.destinationRow);
      this.updateDestinationLayout(this.$refs.destinationRow.getBoundingClientRect().width);
    }
  },
  beforeDestroy() {
    if (this.destinationRowObserver) {
      this.destinationRowObserver.disconnect();
      this.destinationRowObserver = null;
    }
  },
  methods: {
    /**
     * Chooses the destination row layout from its measured width: inline
     * when the three fields fit, the drawer's stacked layout otherwise. A
     * zero width (the form is hidden, e.g. another stepper step is shown) is
     * ignored so the last real measurement is kept.
     *
     * @param {Number} width the destination row's current content width
     * @returns {void}
     */
    updateDestinationLayout(width) {
      if (width) {
        this.rowFitsInline = width >= DESTINATION_INLINE_MIN_WIDTH;
      }
    },
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
