import React from 'react';

function StatsCards({ stats }) {
    const approvalRate = stats.total > 0 ? Math.round((stats.approved / stats.total) * 100) : 0;

    const cards = [
        {
            title: 'Total Payments',
            value: stats.total,
            subtitle: 'Across all submitted flows',
            accent: 'blue'
        },
        {
            title: 'Approved',
            value: stats.approved,
            subtitle: `${approvalRate}% approval rate`,
            accent: 'green'
        },
        {
            title: 'Flagged',
            value: stats.flagged,
            subtitle: 'Multi-rule fraud checks',
            accent: 'red'
        },
        {
            title: 'Pending',
            value: stats.pending,
            subtitle: 'In pipeline now',
            accent: 'orange'
        }
    ];

    return (
        <section className="stats-grid" aria-label="payment stats">
            {cards.map((card) => (
                <article key={card.title} className={`stat-card accent-${card.accent}`}>
                    <p className="stat-title">{card.title}</p>
                    <strong className="stat-value">{card.value}</strong>
                    <span className="stat-subtitle">{card.subtitle}</span>
                </article>
            ))}
        </section>
    );
}

export default StatsCards;
