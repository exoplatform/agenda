<template>
  <div>
    <v-avatar
      left
      size="32"
      class="mx-0 my-auto ">
      <v-img 
        :src="defaultAvatar" />
    </v-avatar>
    <span class=" mx-1 my-auto agenda-guest">
      {{ guestUserEmail }} ({{ $t('agenda.label.guest') }})
    </span>  
  </div>
</template>

<script>
export default {
  props: {
    guest: {
      type: Object,
      default: () => null
    },
  },
  computed: {
    guestUserEmail(){
      return this.guest.guestEmail;
    },
    defaultAvatar() {
      return `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/users/default-image/avatar`;
    },
  },
};
</script>