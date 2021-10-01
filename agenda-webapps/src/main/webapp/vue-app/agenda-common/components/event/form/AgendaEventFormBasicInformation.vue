<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <v-form
    ref="agendaEventForm"
    class="flex"
    flat
    @submit="$emit('next-step')">
    <div class="d-flex flex-column flex-md-row">
      <label class="create-event-label float-left mt-5 me-6 text-subtitle-1 d-none d-md-inline">
        {{ $t('agenda.label.create') }}
      </label>
      <input
        id="eventTitle"
        ref="eventTitle"
        v-model="event.summary"
        :placeholder="$t('agenda.eventTitle')"
        type="text"
        name="title"
        class="ignore-vuetify-classes my-3"
        required
        @change="resetCustomValidity">
      <label class="mt-5 ms-4 me-4 text-subtitle-1 font-weight-bold d-none d-md-inline">
        {{ $t('agenda.label.in') }}
      </label>
      <agenda-event-form-calendar-owner
        ref="calendarOwner"
        :event="event"
        :current-space="currentSpace"
        @initialized="$emit('initialized')" />
    </div>
    <div class="d-flex flex-column flex-md-row mt-1 event-form-body">
      <div class="d-flex flex-column flex-grow-1 event-form-body-left">
        <div v-if="displayTimeInForm && eventDateOption" class="d-flex flex-row">
          <i class="uiIconClock darkGreyIcon uiIcon32x32 mt-4 me-11"></i>
          <agenda-event-form-date-pickers
            :event="eventDateOption"
            class="event-form-datetimes my-4"
            @changed="updateEventDates"
            @initialized="formInitialized" />
        </div>
        <div class="d-flex flex-row">
          <i class="uiIconLocation darkGreyIcon uiIcon32x32 mt-4 me-11"></i>
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
          <i class="uiIconRecurrence darkGreyIcon uiIcon32x32 my-auto me-11"></i>
          <div class="d-flex flex-column">
            <agenda-event-form-recurrence :event="event" class="my-auto" />
            <agenda-event-recurrence
              v-if="hasRecurrence"
              :event="event"
              class="text-wrap ms-2" />
          </div>
        </div>
        <div class="d-flex flex-row">
          <i class="uiIconVideo darkGreyIcon uiIcon32x32 uiIcon32x32 my-3 me-11"></i>
          <div class="d-flex flex-column">
            <agenda-event-form-conference
              :event="event"
              :settings="settings"
              :current-space="currentSpace"
              :conference-provider="conferenceProvider"
              class="my-auto" />
          </div>
        </div>
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0">
            <i class="uiIconDescription darkGreyIcon uiIcon32x32 my-3 me-11"></i>
          </v-flex>
          <extended-textarea
            id="eventDescription"
            ref="eventDescription"
            v-model="event.description"
            :placeholder="$t('agenda.descriptionPlaceholder')"
            :max-length="eventDescriptionTextLength"
            class="pt-2" />
        </div>
      </div>
      <div class="d-none d-md-flex flex-column mx-5 event-form-body-divider ">
        <v-divider vertical />
      </div>
      <div class="d-flex flex-column flex-grow-1 event-form-body-right">
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0 me-2 mt-1">
            <i class="uiIconGroup darkGreyIcon uiIcon32x32 my-3"></i>
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
    reset() {
      this.eventDateOption = null;
      this.$nextTick().then(() => {
        this.eventDateOption = this.event.dateOptions.length === 1 && this.event.dateOptions[0] || this.event;
      });
    },
    resetCustomValidity() {
      if (this.$refs.eventTitle) {
        this.$refs.eventTitle.setCustomValidity('');
      }
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.resetCustomValidity();
      }
    },
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
    validateForm() {
      this.resetCustomValidity();
      this.$refs.calendarOwner.validateForm();

      if (!this.event.summary) {
        this.$refs.eventTitle.setCustomValidity(this.$t('agenda.message.missingEventTitle'));
      } else if (this.event.summary.length < 5 || this.event.summary.length > 1024) {
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