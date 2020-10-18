<template>
  <v-app>
    <agenda-user-general-settings />
  </v-app>
</template>

<script>
export default {
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
  },
};
</script>