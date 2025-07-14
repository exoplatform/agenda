<template>
  <v-hover v-slot="{ hover }">
    <v-card
      flat
      class="pa-0"
      :aria-label="$t('search.access.to.result', {0 : excerptText})"
      :href="eventUrl">
      <v-list class="pa-0" :class="hover && 'light-grey-background-color no-border-radius' || ''">
        <v-list-item>
          <v-list-item-icon class="me-2">
            <span class="d-flex align-center justify-center">
              <v-icon size="32" class="icon-default-color mt-2">fas fa-calendar-alt</v-icon>
            </span>
          </v-list-item-icon>

          <v-list-item-content>
            <v-list-item-title class="d-flex flex-row full-width align-center">
              <h1
                class="flex-grow-1 title font-weight-bold primary--text pt-1 mb-0 ps-0 my-auto align-center text-start text-truncate"
                :aria-label="eventTitleText"
                v-sanitized-html="eventTitle"></h1>
            </v-list-item-title>

            <v-list-item-subtitle class="d-flex flex-column">
              <span class="d-flex flex-row align-center mx-auto full-width">
                <span class="d-flex flex-row align-center">
                  <a
                    v-bind="attrs"
                    v-on="on"
                    :href="calendarOwnerLink"
                    :class="isSpaceEvent && 'spaceAvatar' || ''"
                    class="flex-nowrap flex-shrink-0 d-flex">
                    <v-avatar
                      :size="18"
                      tile
                      class="my-auto">
                      <img
                        :src="eventOwnerAvatarUrl"
                        alt=""
                        class="object-fit-cover ma-auto"
                        loading="lazy">
                    </v-avatar>
                    <p v-if="!isMobile" class="ms-2 my-auto text-subtitle">{{ eventOwnerDisplayName }}</p>
                  </a>
                  <v-icon size="3" class="icon-default-color mx-3">fas fa-circle</v-icon>
                </span>
                <span class="d-flex flex-row align-center">
                  <v-icon
                    size="16"
                    class="icon-default-color">fas fa-calendar-alt</v-icon>
                  <date-format class="ms-1 my-auto" :value="eventStartDate" />
                </span>
                <span v-if="!isMobile && hasRecurrence" class="d-flex flex-row align-center">
                  <v-icon size="3" class="icon-default-color mx-3">fas fa-circle</v-icon>
                  <v-icon size="12" class="pe-1">
                    fas fa-redo
                  </v-icon>
                  <agenda-event-recurrence :event="result" />
                </span>
              </span>
              <div
                v-if="excerptHtml"
                class="pt-2 text-wrap text-body-2 text-color text-break"
                :class="{
                  'text-truncate-2': isMobile,
                  'text-truncate-3': !isMobile,
                }"
                v-sanitized-html="excerptHtml">
              </div>
            </v-list-item-subtitle>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </v-card>
  </v-hover>
</template>

<script>
export default {
  props: {
    result: {
      type: Object,
      default: null,
    },
  },
  computed: {
    eventUrl() {
      return this.result && this.result.id && `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda?eventId=${this.result.id}`;
    },
    eventDescription() {
      return this.result && this.result.description;
    },
    excerpts() {
      return this.result && this.result.excerpts;
    },
    excerptHtml() {
      return this.excerpts.length && this.excerpts.join('\r\n...') || this.eventDescription;
    },
    excerptText() {
      return this.excerpts.length ? this.$utils.htmlToText(this.excerptHtml) : this.eventDescription;
    },
    eventStartDate() {
      return this.result && this.result.start;
    },
    eventTitle() {
      return this.result && this.result.summary || '';
    },
    eventTitleText() {
      return this.$utils.htmlToText(this.eventTitle);
    },
    owner() {
      return this.result && this.result.calendar && this.result.calendar.owner;
    },
    ownerProfile() {
      return this.owner && (this.owner.profile || this.owner.space);
    },
    isSpaceEvent() {
      return Boolean(this.owner?.space);
    },
    calendarOwnerLink() {
      if (this.owner) {
        if (this.owner.providerId === 'organization') {
          return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.owner.remoteId}`;
        } else if (this.owner.providerId === 'space') {
          return `${eXo.env.portal.context}/g/:spaces:${this.owner.remoteId}/`;
        }
      }
      return '';
    },
    eventOwnerDisplayName() {
      return this.ownerProfile && this.ownerProfile.displayName;
    },
    eventOwnerAvatarUrl() {
      return this.ownerProfile && this.ownerProfile.avatarUrl;
    },
    isMobile() {
      return this.$vuetify?.breakpoint?.smAndDown;
    },
    hasRecurrence() {
      return this.result?.recurrence;
    }
  },
};
</script>
