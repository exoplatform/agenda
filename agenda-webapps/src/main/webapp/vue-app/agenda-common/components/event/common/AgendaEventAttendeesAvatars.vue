<template>
  <v-card
    :min-width="avatarsWidth"
    class="transparent d-flex align-center"
    flat
    tabindex="0"
    min-height="42"
    @mouseenter="showSeeMore = true"
    @mouseleave="showSeeMore = false"
    @focusin="showSeeMore = true"
    @focusout="showSeeMore = false"
    @keydown.enter="$emit('open')">
    <div v-if="!showSeeMore" class="d-flex align-center">
      <v-avatar
        v-for="(attendee, index) in visibleAttendees"
        :key="attendee.identity.id || attendee.identity.remoteId"
        :tile="attendee.identity.providerId === 'space'"
        :class="[index > 0 && 'ms-n3', attendee.identity.providerId === 'space' && 'rounded']"
        :size="size"
        class="border-white">
        <v-img 
          :src="attendeeAvatarUrl(attendee)" 
          :lazy-src="defaultAvatarUrl" 
          eager />
      </v-avatar>
      <v-avatar
        v-if="overflowCount > 0"
        :size="size"
        class="ms-n3 grey-lighten1-background border-white">
        <span class="text-body white--text text-center">+{{ overflowCount }}</span>
      </v-avatar>
    </div>
    <v-btn
      v-else
      :min-width="avatarsWidth"
      :min-height="size"
      class="caption white--text grey-lighten1-background px-1"
      v-ripple="false"
      text
      @click.stop="$emit('open')">
      <span class="text-body white--text text-center">{{ $t('agenda.timeline.seeMore') }}</span>
    </v-btn>
  </v-card>
</template>

<script>
export default {
  props: {
    attendees: {
      type: Array,
      default: () => [],
    },
    max: {
      type: Number,
      default: 3,
    },
    size: {
      type: Number,
      default: 34,
    },
  },
  data() {
    return {
      showSeeMore: false,
    };
  },
  computed: {
    visibleAttendees() {
      return this.attendees.slice(0, this.max);
    },
    overflowCount() {
      return Math.max(0, this.attendees.length - this.max);
    },
    avatarsWidth() {
      const count = Math.min(this.attendees.length, this.max) + (this.overflowCount > 0 ? 1 : 0);
      return this.size + (count - 1) * (this.size - 12) + (count * 2);
    },
    defaultAvatarUrl() {
      return '/portal/rest/v1/social/users/default-image/avatar';
    },
  },
  methods: {
    attendeeAvatarUrl(attendee) {
      const profile = attendee.identity.profile || attendee.identity.space || {};
      return profile.avatar || profile.avatarUrl || '/portal/rest/v1/social/users/default-image/avatar';
    },
  },
};
</script>