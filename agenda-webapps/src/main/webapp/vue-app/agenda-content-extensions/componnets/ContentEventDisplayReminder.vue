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
  <v-card
    class="pa-5 white border-box-sizing border-radius box-shadow mt-8"
    :class="{
      'full-width': !mdAndUp,
      'ms-5': mdAndUp
    }"
    :width="mdAndUp && 330"
    outlined>
    <template v-if="loading">
      <v-skeleton-loader type="heading" class="mb-3" />
      <v-skeleton-loader type="text" class="mb-2" />
      <v-skeleton-loader type="text" class="mb-2" />
      <v-skeleton-loader type="text" class="mb-2" />
    </template>
    <template v-else-if="event">
      <div class="d-flex align-center justify-space-between mb-3">
        <span class="text-header text-header-color font-weight-medium">
          {{ $t('contentEvent.reminder.title.label') }}
        </span>
        <v-btn
          min-width="36"
          class="btn btn-primary"
          :href="eventUrl" >
          {{ $t('contentEvent.reminder.replay.label') }}
        </v-btn>
      </div>
      <div
        :class="{
          'd-flex justify-space-between': !mdAndUp && !smAndDown
          }">
        <div class="mb-3 flex-shrink-1 overflow-hidden">
          <div class="text-color text-font-size font-weight-bold mb-5">
            {{ $t('contentEvent.reminder.highlights.label') }}
          </div>
          <div class="d-flex align-center mb-3">
            <v-sheet
              class="me-4 d-flex align-center justify-center"
              width="18">
              <v-icon
                size="20"
                class="icon-default-color">
                far fa-clock
              </v-icon>
            </v-sheet>
            <span>{{ duration }}</span>
          </div>
          <div
            v-if="hasRecurrence"
            class="d-flex mb-3">
            <v-sheet
              class="me-4 d-flex align-center justify-center"
              width="18">
              <v-icon
                size="20"
                class="icon-default-color">
                fas fa-sync-alt
              </v-icon>
            </v-sheet>
            <agenda-event-recurrence :event="event" />
          </div>
          <div v-if="locationHighlight" class="d-flex align-center">
            <v-sheet
              class="me-4 d-flex align-center justify-center"
              width="18">
              <v-icon
                :size="20"
                class="icon-default-color">
                fas fa-map-marker-alt
              </v-icon>
            </v-sheet>
            <span>{{ locationHighlight }}</span>
          </div>
        </div>
        <div class="flex-shrink-0">
          <div class="text-color text-font-size font-weight-bold mb-5">
            {{ $t('contentEvent.reminder.Details.label') }}
          </div>
          <div class="d-flex align-center mb-3">
            <v-sheet
              class="me-4 d-flex align-center justify-center"
              width="18">
              <v-icon
                size="20"
                class="icon-default-color">
                far fa-calendar-alt
              </v-icon>
            </v-sheet>
            <div class="d-flex flex-nowrap align-center">
              <date-format
                :value="eventStart"
                :format="fullDateFormat" />
              <div
                v-if="!isAllDayEvent"
                class="d-flex flex-nowrap align-center">
                <span class="mx-1">·</span>
                <date-format
                  :value="eventStart"
                  :format="timeFormat" />
              </div>
            </div>
          </div>
          <div
            v-if="eventLocation"
            class="d-flex align-center mb-3">
            <v-sheet
              class="me-4 d-flex align-center justify-center"
              width="18">
              <v-icon
                size="20"
                class="icon-default-color">
                fas fa-map-marker-alt
              </v-icon>
            </v-sheet>
            <span class="text-truncate">
              {{ eventLocation }}
            </span>
            <v-spacer />
            <a
              :href="mapsUrl"
              target="_blank">
              <v-icon
                size="20"
                class="icon-default-color">
                fas fa-directions
              </v-icon>
            </a>
          </div>
          <div
            v-if="webConferenceLink"
            class="d-flex align-center">
            <v-sheet
              class="me-4 d-flex align-center justify-center"
              width="18">
              <v-icon
                size="20"
                class="icon-default-color">
                fas fa-video
              </v-icon>
            </v-sheet>
            <v-btn
              :href="webConferenceLink"
              height="24"
              class="btn btn-primary border-radius-16 px-3"
              target="_blank"
              x-small
              outlined>
              {{ $t('contentEvent.reminder.join.label') }}
            </v-btn>
          </div>
          <div
            v-if="eventLocation && mdAndUp"
            class="mt-3 position-relative">
            <v-skeleton-loader
              v-if="!mapLoaded"
              type="image"
              height="200"
              class="border-radius" />
            <iframe
              v-if="event && eventLocation"
              :src="mapEmbedUrl"
              :title="$t('contentEvent.location.label')"
              :class="mapLoaded ? '' : 'position-absolute t-0'"
              class="border-radius no-border"
              width="100%"
              height="200"
              loading="lazy"
              @load="mapLoaded = true"></iframe>
          </div>
        </div>
      </div>
    </template>
  </v-card>
</template>

<script>

export default {
  data() {
    return {
      expand: 'attendees,reminders,recurrence,conferences',
      event: null,
      mapLoaded: false,
      loading: false
    };
  },
  props: {
    eventId: {
      type: Object,
      default: null
    }
  },
  computed: {
    mdAndUp () {
      return this.$vuetify.breakpoint.width >= this.$vuetify.breakpoint.thresholds.md;
    },
    smAndDown () {
      return this.$vuetify.breakpoint.width <= this.$vuetify.breakpoint.thresholds.sm;
    },
    mapEmbedUrl() {
      return `https://maps.google.com/maps?q=${encodeURIComponent(this.eventLocation)}&output=embed`;
    },
    eventStart() {
      return this.event?.start;
    },
    eventEnd() {
      return this.event?.end;
    },
    tz() {
      return this.event?.timeZoneId || 'UTC';
    },
    fullDateFormat() {
      return {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        timeZone: this.tz,
      };
    },
    timeFormat() {
      return {
        hour: '2-digit',
        minute: '2-digit',
        timeZoneName: 'short',
        timeZone: this.tz,
      };
    },
    webConferenceLink() {
      return this.event?.conferences?.[0]?.url;
    },
    eventLocation() {
      return this.event?.location;
    },
    isAllDayEvent() {
      return this.event?.allDay;
    },
    duration() {
      if (this.isAllDayEvent) {
        return this.$t('agenda.allDay');
      }
      const ms = new Date(this.eventEnd) - new Date(this.eventStart);
      const h = Math.floor(ms / 3600000);
      const m = Math.floor((ms % 3600000) / 60000);
      if (h && m) {
        return `${h} ${this.$t(`contentEvent.reminder.hour${h > 1 ? 's' : ''}.label`)} ${m}
         ${this.$t('contentEvent.reminder.minutes.label')}`;
      }
      if (h) {
        return `${h} ${this.$t(`contentEvent.reminder.hour${h > 1 ? 's' : ''}.label`)}`;
      }
      return `${m} ${this.$t('contentEvent.reminder.minutes.label')}`;
    },
    hasRecurrence() {
      return  this.event?.recurrence;
    },
    locationHighlight() {
      const hasConference = !!this.webConferenceLink;
      const hasLocation = !!this.eventLocation;
      if (hasLocation && hasConference) {
        return `${this.$t('contentEvent.reminder.online.label')} · ${this.$t('contentEvent.reminder.inperson.label')}`;
      }
      if (hasConference) {
        return this.$t('contentEvent.reminder.online.label');
      }
      if (hasLocation) {
        return this.$t('contentEvent.reminder.inperson.label');
      }
      return null;
    },
    mapsUrl() {
      return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(this?.event.location)}`;
    },
    eventUrl() {
      return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda?eventId=${this.event?.id}`;
    }
  },
  watch: {
    eventLocation() {
      this.mapLoaded = false;
    }
  },
  created() {
    this.init();
    document.addEventListener('content-event-updated', this.init);
  },
  beforeDestroy() {
    document.removeEventListener('content-event-updated', this.init);
  },
  methods: {
    async init() {
      this.loading = true;
      this.event = null;
      try {
        this.event = await this.$eventService.getEventById(this.eventId, this.expand);
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
