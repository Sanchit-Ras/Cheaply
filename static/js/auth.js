document.addEventListener('DOMContentLoaded', function() {
    // Handle switching between login and signup tabs
    document.querySelectorAll('.switch-auth').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const targetTabId = this.getAttribute('data-target');
            const targetTab = document.getElementById(targetTabId);
            bootstrap.Tab.getOrCreateInstance(targetTab).show();
        });
    });

    // Handle form validation
    const forms = document.querySelectorAll('.auth-form');
    
    Array.from(forms).forEach(form => {
        form.addEventListener('submit', event => {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
            }
            
            form.classList.add('was-validated');
        });
    });

    // Add animation to the auth card
    const authCard = document.querySelector('.auth-card');
    if (authCard) {
        authCard.classList.add('fade-in');
    }
}); 