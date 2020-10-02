<template>
  <exo-drawer
    ref="mobileEventFormDrawer"
    right
    body-classes="hide-scroll"
    class="mobileEventFormDrawer"
    @closed="cancelEventCreation">
    <template slot="title">
      {{ $t('agenda.title.addEvent') }}
    </template>
    <template slot="content">
      <v-form
        v-if="event"
        ref="agendaEventForm"
        class="flex event-form-mobile px-3 pb-2"
        flat
        @submit="createEvent">
        <div class="d-flex flex-column flex-grow-1">
          <label class="font-weight-bold my-2">
            {{ $t('agenda.title') }}
          </label>
          <input
            id="eventTitle"
            ref="eventTitle"
            v-model="event.summary"
            :placeholder="$t('agenda.eventTitle')"
            type="text"
            name="title"
            class="ignore-vuetify-classes"
            required
            autofocus
            @change="resetCustomValidity">
          <label class="font-weight-bold my-2">
            {{ $t('agenda') }}
          </label>
          <agenda-event-form-calendar-owner
            ref="calendarOwner"
            :event="event"
            :current-space="currentSpace" />
          <agenda-event-form-date-pickers :event="event">
            <template slot="startDateLabel">
              <label v-if="event.allDay" class="font-weight-bold my-2">{{ $t('agenda.startDate') }}</label>
              <label v-else class="font-weight-bold my-2">{{ $t('agenda.startTime') }}</label>
            </template>
            <template slot="endDateLabel">
              <label v-if="event.allDay" class="font-weight-bold my-2">{{ $t('agenda.endDate') }}</label>
              <label v-else class="font-weight-bold my-2">{{ $t('agenda.endTime') }}</label>
            </template>
          </agenda-event-form-date-pickers>
          <label class="font-weight-bold my-2">
            {{ $t('agenda.location') }}
          </label>
          <input
            id="eventLocation"
            ref="eventLocation"
            v-model="event.location"
            :placeholder="$t('agenda.eventLocation')"
            type="text"
            name="locationEvent"
            class="ignore-vuetify-classes my-0 location-event-input">
          <label class="font-weight-bold my-2">
            {{ $t('agenda.description') }}
          </label>
          <extended-textarea
            id="eventDescription"
            ref="eventDescription"
            v-model="event.description"
            :placeholder="$t('agenda.descriptionPlaceholder')"
            :max-length="eventDescriptionTextLength"
            class="py-0 my-0" />
          <label class="font-weight-bold my-2">
            {{ $t('agenda.participants') }}
          </label>
          <agenda-event-form-attendees :event="event" />
        </div>
      </v-form>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn ml-2 d-inline d-sm-none"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          :disabled="disableSaveButton"
          class="btn btn-primary ml-2"
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
  },
  data: () => ({
    event: null,
    saving: false,
    eventDescriptionTextLength: 1300
  }),
  computed: {
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
    eventDescription() {
      return this.event && this.event.description || '';
    },
    eventDescriptionValid() {
      return this.eventDescription.length <= 1300;
    },
    disableSaveButton() {
      return this.saving || !this.eventTitleValid || !this.eventOwnerValid || !this.eventDescriptionValid;
    },
  },
  created() {
    this.$root.$on('agenda-event-mobile-form-open', event => {
      this.event = null;
      this.$nextTick().then(() => {
        this.event = event;
        this.open();
        this.$nextTick().then(() => this.$root.$emit('agenda-event-mobile-form-opened', this.event));
      });
    });
  },
  methods:{
    close() {
      this.$refs.mobileEventFormDrawer.close();
    },
    open() {
      this.resetCustomValidity();
      this.$refs.mobileEventFormDrawer.open();
      window.setTimeout(() => {
        if (this.$refs.eventTitle) {
          this.$refs.eventTitle.focus();
        }
      }, 200);
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
        .then(() => {
          this.$root.$emit('refresh');
          this.close();
        })
        .finally(() => this.saving = false);
    },
  }
};
</script>