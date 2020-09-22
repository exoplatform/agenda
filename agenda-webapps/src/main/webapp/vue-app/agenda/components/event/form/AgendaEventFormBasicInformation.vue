<template>
  <v-form
    ref="agendaEventForm"
    class="flex"
    flat
    @submit="$emit('next-step')">
    <div class="d-flex flex-column flex-md-row">
      <label class="create-event-label float-left mt-5 mr-6 text-subtitle-1 d-none d-md-inline">
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
      <label class="mt-5 ml-4 mr-4 text-subtitle-1 font-weight-bold d-none d-md-inline">
        {{ $t('agenda.label.in') }}
      </label>
      <agenda-event-form-calendar-owner
        ref="calendarOwner"
        :event="event"
        :current-space="currentSpace" />
    </div>
    <div class="d-flex flex-column flex-md-row mt-1 event-form-body">
      <div class="d-flex flex-column flex-grow-1 event-form-body-left">
        <div class="d-flex flex-row">
          <i class="uiIconLocation darkGreyIcon uiIcon32x32 my-3 mr-11"></i>
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
          <v-flex class="flex-grow-0 mt-1">
            <i class="uiIconReminder darkGreyIcon uiIcon32x32 my-3 mr-11"></i>
          </v-flex>
          <agenda-event-form-reminders :event="event" />
        </div>
        <div class="d-flex flex-row">
          <i class="uiIconRecurrence darkGreyIcon uiIcon32x32 my-3 mr-11"></i>
          <agenda-event-form-recurrence :event="event" />
        </div>
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0">
            <i class="uiIconDescription darkGreyIcon uiIcon32x32 my-3 mr-11"></i>
          </v-flex>
          <textarea
            id="eventDescription"
            ref="eventDescription"
            v-model="event.description"
            :placeholder="$t('agenda.description')"
            type="text"
            name="description"
            rows="20"
            maxlength="2000"
            class="ignore-vuetify-classes my-3 description-event-textarea textarea-no-resize">
          </textarea>
        </div>
      </div>
      <div class="d-none d-md-flex flex-column mx-5 event-form-body-divider ">
        <v-divider vertical />
      </div>
      <div class="d-flex flex-column flex-grow-1 event-form-body-right">
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0 mr-2 mt-1">
            <i class="uiIconGroup darkGreyIcon uiIcon32x32 my-3"></i>
          </v-flex>
          <agenda-event-form-attendees :event="event" />
        </div>
        <div class="d-flex flex-row">
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.modifyEventPermission') }}</label>
          <v-switch v-model="event.allowAttendeeToUpdate" class="mt-0 ml-4" />
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
    currentSpace: {
      type: Object,
      default: () => null,
    },
  },
  computed: {
    allowAttendeeToUpdate() {
      return this.event.allowAttendeeToUpdate;
    },
  },
  watch: {
    allowAttendeeToUpdate() {
      if (this.allowAttendeeToUpdate) {
        this.event.allowAttendeeToInvite = true;
      }
    },
  },
  created() {
    this.$root.$on('agenda-event-form-opened', () => {
      this.$nextTick().then(this.resetCustomValidity);
    });
  },
  methods:{
    resetCustomValidity() {
      if (this.$refs.eventTitle) {
        this.$refs.eventTitle.setCustomValidity('');
      }
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.resetCustomValidity();
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