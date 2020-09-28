<template>
  <v-list-item class="d-flex attendee mb-5">
    <v-list-item-avatar>
      <v-avatar size="32">
        <v-img :src="attendeeProfileAvatarUrl" />
      </v-avatar>
    </v-list-item-avatar>
    <v-list-item-content>
      <a :href="attendeeProfileLink" class="mr-5 my-auto text-truncate">
        {{ attendeeProfileDisplayName }}
      </a>
    </v-list-item-content>
    <v-list-item-action>
      <span :class="responseIconResponse"></span>
    </v-list-item-action>
  </v-list-item>
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
      return this.attendee && this.attendee.response && `attendee-response attendee-response-${this.attendee.response.toLowerCase()}`;
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