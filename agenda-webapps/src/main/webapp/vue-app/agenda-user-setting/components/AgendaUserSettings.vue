<template>
  <v-app>
    <v-card class="ma-4 border-radius" flat>
      <v-list two-line>
        <agenda-user-general-settings :skeleton="skeleton" />
        <agenda-user-connector-settings :skeleton="skeleton" />
      </v-list>
    </v-card>
  </v-app>
</template>

<script>
export default {
  data: () => ({
    skeleton: true,
  }),
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
    this.skeleton = false;
  },
};
</script>