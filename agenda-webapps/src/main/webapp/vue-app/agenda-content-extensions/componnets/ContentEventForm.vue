<!--
 Copyright (C) 2026 eXo Platform SAS.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
-->

<template>
  <div>
    <div class="d-flex justify-space-between">
      <span class="my-auto">
        {{ $t('contentEvent.add.event.label') }}
      </span>
      <v-switch
        v-model="eventTypeEnabled"
        :ripple="false"
        color="primary"
        class="my-auto"
        hide-details />
    </div>
    <div
      v-if="eventTypeEnabled"
      class="mt-5 width-fit-content">
      <p :class="{'mb-3': !expanded}">
        {{ $t('contentEvent.hours.label') }}
      </p>
      <v-row
        :class="{'d-block': !expanded}"
        align="center"
        class="mt-1">
        <v-col
          :class="{
            'mb-1': !expanded,
            'me-5': expanded
          }"
          cols="1"
          class="py-0 text-left">
          <span>{{ $t('contentEvent.from.label') }}</span>
        </v-col>
        <v-col class="d-flex py-0">
          <v-menu
            v-model="startDateMenu"
            :close-on-content-click="true"
            :nudge-right="40"
            transition="scale-transition"
            content-class="ms-n10"
            offset-y
            min-width="auto">
            <template #activator="{ on, attrs }">
              <v-text-field
                v-model="formattedStartDate"
                v-bind="attrs"
                v-on="on"
                :aria-label="$t('notes.publication.startDate.label')"
                class="pt-0 border-box-sizing flex-grow-0"
                readonly
                outlined
                dense />
            </template>
            <v-date-picker
              v-model="startDate"
              :min="minStartDate"
              :locale="locale"
              @input="updateMinStartTime" />
          </v-menu>
          <time-picker
            v-if="!event?.allDay"
            v-model="startTime"
            :min="minStartTime"
            :aria-label="$t('notes.publication.startTime.label')"
            format="ampm"
            type="time"
            class="mb-1 ms-2" />
        </v-col>
      </v-row>
      <v-row
        :class="{'d-block': !expanded}"
        align="center"
        class="mt-1">
        <v-col
          :class="{
            'mb-1': !expanded,
            'me-5': expanded
          }"
          cols="1"
          class="py-0 text-left">
          <span>{{ $t('contentEvent.to.label') }}</span>
        </v-col>
        <v-col class="d-flex py-0">
          <v-menu
            v-model="endDateMenu"
            :close-on-content-click="true"
            :nudge-right="40"
            transition="scale-transition"
            content-class="ms-n10"
            offset-y
            min-width="auto">
            <template #activator="{ on, attrs }">
              <v-text-field
                v-model="formattedEndDate"
                v-bind="attrs"
                v-on="on"
                :aria-label="$t('notes.publication.endDate.label')"
                class="pt-0 border-box-sizing flex-grow-0"
                readonly
                outlined
                dense />
            </template>
            <v-date-picker
              v-model="endDate"
              :min="minEndDate"
              :locale="locale"
              @input="updateEndMinTime" />
          </v-menu>
          <time-picker
            v-if="!event?.allDay"
            v-model="endTime"
            :min="minEndTime"
            :aria-label="$t('notes.publication.endTime.label')"
            format="ampm"
            type="time"
            class="mb-1 ms-2" />
        </v-col>
      </v-row>
      <div class="d-flex mt-2">
        <v-switch
          v-model="event.allDay"
          :ripple="false"
          color="primary"
          class="my-auto"
          hide-details />
        <span class="ms-3 my-auto">
          {{ $t('contentEvent.allDay.label') }}
        </span>
      </div>
      <div class="mt-4">
        <span class="my-auto mb-2">
          {{ $t('contentEvent.recurrence.label') }}
        </span>
        <agenda-event-form-recurrence :event="event" />
      </div>
      <div class="mt-4">
        <span class="my-auto mb-2">
          {{ $t('contentEvent.location.label') }}
        </span>
        <v-text-field
          v-model="event.location"
          :placeholder="$t('contentEvent.add.location.label')"
          class="pt-0 mt-3"
          outlined
          dense
          hide-details
          required />
      </div>
      <div class="mt-4">
        <span class="my-auto mb-2">
          {{ $t('contentEvent.webconferencing.link.label') }}
        </span>
        <agenda-event-form-conference
          :event="event"
          :current-space="currentSpace"
          :conference-provider="conferenceProvider"
          :show-icon="false" />
      </div>
      <div class="mt-n1">
        <span class="my-auto mb-2">
          {{ $t('contentEvent.participants.label') }}
        </span>
        <agenda-event-form-attendees
          :event="event" />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    const timeSlot = 15;
    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];
    const {startTime, endTime} = this.initStartAndEndTime(timeSlot);
    return {
      currentSpace: null,
      conferenceProviders: null,
      event: {
        start: null,
        end: null,
        allDay: false,
        location: '',
        recurrence: {},
        calendar: {
          owner: {}
        },
        attendees: []
      },
      locale: eXo?.env?.portal?.language,
      timeSlot,
      eventTypeEnabled: false,
      startDateMenu: false,
      endDateMenu: false,
      startDate: todayStr,
      minStartDate: todayStr,
      endDate: todayStr,
      minEndDate: todayStr,
      startTime,
      endTime,
      minStartTime: startTime,
      minEndTime: endTime,
    };
  },
  props: {
    spaceId: {
      type: Number,
      default: null
    },
    expanded: {
      type: Boolean,
      default: false
    }
  },
  created() {
    this.initCalendarOwner();
  },
  mounted() {
    this.fillEventObject();
  },
  watch: {
    startTime() {
      if (this.startDate === this.endDate) {
        const newEnd = this.addMinutes(this.startTime, this.timeSlot);
        if (this.endTime !== newEnd) {
          this.endTime = newEnd;
        }
      }
      this.updateEndMinTime();
      this.updateEvent();
    },
    endTime: 'updateEvent',
    startDate: 'updateEvent',
    endDate: 'updateEvent',
    'event.allDay': function (allDay) {
      if (allDay) {
        this.startTime = '';
        this.endTime = '';
      } else {
        const { startTime, endTime } = this.initStartAndEndTime();
        this.startTime = startTime;
        this.endTime = endTime;
      }
      this.updateEvent();
    }
  },
  computed: {
    formattedStartDate() {
      return this.startDate && this.formatDate(this.startDate) || '';
    },
    formattedEndDate() {
      return this.endDate && this.formatDate(this.endDate) || '';
    },
    enabledConferenceProviderName() {
      return this.conferenceProviders
          && this.conferenceProviders.length > 0
          && this.conferenceProviders.find((provider) => provider.configured);
    },
    conferenceProvider() {
      return this.conferenceProviders && this.enabledConferenceProviderName
          && this.conferenceProviders.find(provider => provider.isInitialized &&
              provider.linkSupported
              && provider.groupSupported
              && this.enabledConferenceProviderName.getType() === provider.getType());
    },
  },
  methods: {
    loadWebconferencingProviders(spacePrettyName) {
      if (spacePrettyName) {
        this.$webConferencingService.getAllProviders(spacePrettyName).then(providers => {
          this.conferenceProviders = providers;
          return this.$nextTick();
        });
      } else {
        this.conferenceProviders = null;
      }
    },
    initCalendarOwner() {
      this.$spaceService.getSpaceById(this.spaceId, 'identity').then((space) => {
        this.currentSpace = space;
        this.loadWebconferencingProviders(this.currentSpace.prettyName);
        this.event.calendar.owner = {
          id: `space:${this.currentSpace.prettyName}`,
          remoteId: this.currentSpace.prettyName,
          providerId: 'space',
          profile: {
            avatarUrl: this.currentSpace.avatarUrl,
            fullName: this.currentSpace.displayName,
          },
        };
      });
    },
    initStartAndEndTime(timeSlot) {
      timeSlot ??= this.timeSlot;
      const today = new Date();
      const startTime = this.roundUpToSlot(today, timeSlot);
      const endTime = this.addMinutes(startTime, timeSlot);
      return {startTime, endTime};
    },
    updateEvent() {
      this.fillEventObject();
    },
    fillEventObject() {
      this.event.start = this.computeDateTime(this.startDate, this.startTime);
      this.event.end = this.computeDateTime(this.endDate, this.endTime);
    },
    roundUpToSlot(date, slot) {
      const totalMins = date.getHours() * 60 + date.getMinutes();
      const remainder = totalMins % slot;
      const rounded = remainder === 0 ? totalMins : totalMins + (slot - remainder);
      const h = Math.floor(rounded / 60) % 24;
      const m = rounded % 60;
      return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
    },
    addMinutes(timeStr, minutes) {
      const [h, m] = timeStr.split(':').map(Number);
      const total = h * 60 + m + minutes;
      return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
    },
    formatDate(date) {
      const options = {year: 'numeric', month: 'long', day: 'numeric'};
      return date && new Intl.DateTimeFormat(this.locale, options).format(new Date(date));
    },
    computeDateTime(date, time) {
      if (!date && !time) {
        return null;
      }
      const computedDate = new Date(date);
      if (time) {
        let hours;
        let minutes;
        if (time instanceof Date) {
          const dateTime = new Date(time);
          hours = dateTime.getHours();
          minutes = dateTime.getMinutes();
        } else {
          [hours, minutes] = time.split(':').map(Number);
        }
        computedDate.setHours(hours);
        computedDate.setMinutes(minutes);
        computedDate.setSeconds(0);
      }
      return computedDate.toISOString();
    },
    updateMinStartTime() {
      const todayStr = new Date().toISOString().split('T')[0];

      if (this.startDate === todayStr) {
        this.minStartTime = this.roundUpToSlot(new Date(), this.timeSlot);
        if (this.startTime < this.minStartTime) {
          this.startTime = this.minStartTime;
        }
      } else {
        this.minStartTime = null;
      }
      this.minEndDate = this.startDate;
      if (this.endDate < this.startDate) {
        this.endDate = this.startDate;
      }

      this.updateEndMinTime();
    },
    updateEndMinTime() {
      if (this.endDate === this.startDate) {
        this.minEndTime = this.addMinutes(this.startTime, this.timeSlot);
      } else {
        this.minEndTime = null;
      }
      if (this.minEndTime && this.endTime < this.minEndTime) {
        this.endTime = this.minEndTime;
      }
    },
  },
};
</script>
