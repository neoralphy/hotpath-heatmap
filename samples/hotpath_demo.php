<?php

// Open this file in the sandbox IDE (./gradlew runIde) to see the heatmap.
// The call sites in OrderController should light up yellow/orange/red.

namespace Demo;

class UserRepository
{
    public function findActiveUsers(): array
    {
        // Pretend DB access.
        return [];
    }

    public function findOrdersFor(int $userId): array
    {
        return [];
    }
}

class MailClient
{
    public function send(string $to, string $body): void
    {
        // Pretend HTTP/SMTP call.
    }
}

class InvoiceService
{
    public function __construct(
        private UserRepository $users,
        private MailClient $mail,
    ) {}

    public function buildInvoices(): void
    {
        // Nested loop + repository + client downstream.
        foreach ($this->users->findActiveUsers() as $user) {
            foreach ($this->users->findOrdersFor($user) as $order) {
                $this->mail->send('x@y.z', 'invoice');
            }
        }
    }
}

class BillingService
{
    public function __construct(private InvoiceService $invoices) {}

    public function calculate(): void
    {
        $this->invoices->buildInvoices();
    }
}

class OrderController
{
    public function __construct(private BillingService $billing) {}

    public function cheapLooking(array $items): void
    {
        // This innocent-looking line should be flagged HIGH / VERY HIGH:
        // calculate() -> buildInvoices() -> nested foreach -> repository + mail client.
        foreach ($items as $item) {
            $this->billing->calculate();
        }
    }

    public function trulyCheap(): int
    {
        // Should stay unhighlighted: no downstream cost.
        return 1 + 1;
    }
}
