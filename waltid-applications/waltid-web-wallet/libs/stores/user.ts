import { useLocalStorage } from "@vueuse/core";
import { defineStore } from "pinia";
import { ref } from "vue";

export const useUserStore = defineStore("userStore", () => {
    const user = ref(useLocalStorage("id/walt/wallet/user", { id: "", email: "n/a" }));

    return { user };
});