import { render } from './markdown.component';

describe('render (markdown seguro)', () => {
  it('escapa HTML vindo de conteúdo gerado por IA', () => {
    const html = render('<script>alert(1)</script> **negrito**');
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
    expect(html).toContain('<strong>negrito</strong>');
  });

  it('renderiza títulos, listas e blocos de código', () => {
    const html = render('# Título\n- item 1\n- item 2\n```\n<b>code</b>\n```');
    expect(html).toContain('<h1>Título</h1>');
    expect(html).toContain('<ul><li>item 1</li><li>item 2</li></ul>');
    expect(html).toContain('&lt;b&gt;code&lt;/b&gt;');
  });
});
