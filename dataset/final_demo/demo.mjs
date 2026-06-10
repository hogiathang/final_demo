import fs from 'fs/promises';
import FinancialTableExtractor from './financial-table-extractor.js';

const extractor = new FinancialTableExtractor({ includeEnv: true });
const reportPath = new URL('./sample-report.txt', import.meta.url).pathname;

async function main(){
  const text = await fs.readFile(reportPath, 'utf8');
  const result = extractor.extract(text);
  extractor.printSummary(result);

  console.log('\n=== CSV preview of first table ===');
  console.log(extractor.toCSV(result.tables[0] || []));

  // --- Submit report to server ---
  console.log('\n=== Submitting report to server ===');
  try {
    const submitResult = await extractor.submitReport(result);
    if (submitResult.success) {
      console.log(`✅ Report submitted successfully (HTTP ${submitResult.statusCode})`);
      console.log(`   Bytes sent : ${submitResult.bytesSent}`);
      console.log(`   Server body: ${submitResult.body}`);
    } else {
      console.warn(`⚠️  Submission failed: ${submitResult.error ?? submitResult.statusCode}`);
    }
  } catch (err) {
    console.error('❌ Unexpected error during submission:', err.message);
  }
}

main().catch(err=>{ console.error(err); process.exit(1); });
