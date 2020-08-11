<template>
  <v-app class="agenda-application transparent" flat>
    <main>
      <v-card class="transparent ma-5" flat>
        New App
      </v-card>
    </main>
  </v-app>
</template>

<script>
export default {
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
  }
};
</script>
