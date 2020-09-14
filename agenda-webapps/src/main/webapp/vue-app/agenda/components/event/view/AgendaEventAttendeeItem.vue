<template>
  <div class="d-flex attendee mb-5">
    <v-avatar
      size="32"
      left
      class="mr-5">
      <v-img :src="attendeeProfileAvatarUrl" />
    </v-avatar>
    <a :href="attendeeProfileLink" class="mr-5">
      {{ attendeeProfileDisplayName }}
    </a>
    <i :class="responseIconResponse"></i>
  </div>
</template>
<script>
export default {
  props: {
    attendee: {
      type: Object,
      default: () => ({})
    },
  },
  computed: {
    responseIconResponse() {
      return this.attendee && this.attendee.response && `uiIcon-attendee-response-${this.attendee.response.toLowerCase()}`;
    },
    attendeeProfileLink() {
      return this.attendee.identity.space ? this.attendee.identity.space.url : this.attendee.identity.profile ? this.attendee.identity.profile.urls : '';
    },
    attendeeProfileAvatarUrl() {
      return this.attendee.identity.space ? this.attendee.identity.space.avatarUrl : this.attendee.identity.profile ? this.attendee.identity.profile.avatar : '';

    },
    attendeeProfileDisplayName() {
      return this.attendee.identity.space ? this.attendee.identity.space.displayName : this.attendee.identity.profile ? this.attendee.identity.profile.fullname : '';
    },
  }
};
</script>